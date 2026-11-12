package com.example.bible

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.bible.data.TranslationLibrary
import com.example.bible.data.importer.ImportException
import com.example.bible.data.importer.Importer
import com.example.bible.data.importer.ParsedBible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bible.data.BibleRepository
import com.example.bible.data.Books
import com.example.bible.data.Chapter
import com.example.bible.data.CompletionResult
import com.example.bible.data.ProgressStore
import com.example.bible.data.ReadingPlan
import com.example.bible.data.Reminders
import com.example.bible.data.SearchResults
import com.example.bible.data.SearchScope
import com.example.bible.data.Translation
import kotlinx.coroutines.launch

enum class Tab { Path, Bible, Search, Profile }

sealed interface Screen {
    data class Home(val tab: Tab = Tab.Path) : Screen
    data class Lesson(val dayIndex: Int) : Screen
    data class Complete(val result: CompletionResult) : Screen
    data class StreakCelebration(val result: CompletionResult) : Screen
    /** [verse], when set, is scrolled to and highlighted (e.g. opened from search). */
    data class Reader(val bookId: String, val chapter: Int, val verse: Int? = null) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val plan: ReadingPlan = ReadingPlan.load(app)
    private val bible = BibleRepository(app)
    val library = TranslationLibrary(app)
    val translations = library.all
    private val store = ProgressStore(app, library)
    val progress = store.state
    /** Reads chapters aloud with the on-device TTS engine. */
    val narrator = com.example.bible.data.Narrator.get(app)
    /** Verse highlights and notes. */
    val annotations = com.example.bible.data.AnnotationStore(app)

    /** Simple back stack; the last entry is on screen. */
    val stack = mutableStateListOf<Screen>(Screen.Home())

    init {
        // Re-arm the daily reminder in case the alarm was lost (e.g. app force-stopped).
        progress.value.let { Reminders.schedule(app, it.reminderEnabled, it.reminderMinute) }
        // Warm up the current translation so the first chapter opens instantly.
        viewModelScope.launch { bible.chapter(progress.value.translation, "GEN", 1) }
    }

    fun push(screen: Screen) { stack.add(screen) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun replaceTop(screen: Screen) { stack[stack.lastIndex] = screen }
    fun goHome(tab: Tab? = null) {
        val home = stack.first() as Screen.Home
        stack.clear()
        stack.add(if (tab != null) home.copy(tab = tab) else home)
    }
    /**
     * Switching to the Bible tab from another tab reopens the last chapter read (when
     * [resumeReading] is on); tapping it while already there stays on the book list so you can browse.
     */
    fun selectTab(tab: Tab) {
        val alreadyThere = (stack[0] as? Screen.Home)?.tab == tab
        stack[0] = Screen.Home(tab)
        if (tab == Tab.Bible && !alreadyThere && stack.size == 1 && resumeReading) {
            lastChapter?.let { (book, n) -> push(Screen.Reader(book, n)) }
        }
    }

    // ---- Last chapter read (survives restarts) ----

    private val reading = app.getSharedPreferences("reading", Context.MODE_PRIVATE)

    /** The chapter most recently open in the reader, if any. */
    var lastChapter by mutableStateOf(
        reading.getString("book", null)
            ?.takeIf { id -> Books.all.any { it.id == id } }
            ?.let { id -> id to reading.getInt("chapter", 1).coerceIn(1, Books[id].chapters) }
    )
        private set

    /** Setting: reopen the last chapter when coming back to the Bible tab. Off by default. */
    var resumeReading by mutableStateOf(reading.getBoolean("resume", false))
        private set

    fun changeResumeReading(on: Boolean) {
        resumeReading = on
        reading.edit().putBoolean("resume", on).apply()
    }

    /** Book the Bible tab should open on: the last one read, if resuming is on. */
    val resumeBook get() = lastChapter?.first?.takeIf { resumeReading }

    fun rememberChapter(bookId: String, chapter: Int) {
        if (lastChapter == (bookId to chapter)) return
        lastChapter = bookId to chapter
        reading.edit().putString("book", bookId).putInt("chapter", chapter).apply()
    }

    suspend fun chapter(bookId: String, number: Int): Chapter =
        bible.chapter(progress.value.translation, bookId, number)

    /** Translation menu; shown from the app root so it covers the whole screen. */
    var translationSheetOpen by mutableStateOf(false)

    // Search tab state lives here so it survives switching tabs and opening a result.
    var searchQuery by mutableStateOf("")
    var searchScope by mutableStateOf(SearchScope.All)
    var searchResults by mutableStateOf<SearchResults?>(null)

    suspend fun search(query: String, scope: SearchScope): SearchResults =
        bible.search(progress.value.translation, query, scope)

    fun completeDay(dayIndex: Int) {
        val chapters = plan.days[dayIndex].chapters.size
        val result = store.completeDay(dayIndex, chapters)
        replaceTop(Screen.Complete(result))
    }

    fun setTranslation(t: Translation) = store.setTranslation(t)

    // ---- Importing translations ----

    sealed interface ImportState {
        data object Reading : ImportState
        data class Preview(val bible: ParsedBible, val fileName: String) : ImportState
        data object Saving : ImportState
        data class Failed(val message: String) : ImportState
    }

    var importState by mutableStateOf<ImportState?>(null)
        internal set

    fun startImport(uri: Uri) {
        importState = ImportState.Reading
        viewModelScope.launch {
            importState = try {
                val app = getApplication<Application>()
                val name = withContext(Dispatchers.IO) { displayName(app, uri) } ?: "Imported Bible"
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw ImportException("The file couldn't be opened.")
                val bible = withContext(Dispatchers.Default) { Importer.parse(bytes, name) }
                ImportState.Preview(bible, name)
            } catch (e: ImportException) {
                ImportState.Failed(e.message ?: "The file couldn't be imported.")
            } catch (e: OutOfMemoryError) {
                ImportState.Failed("That file is too large to import.")
            } catch (e: Exception) {
                ImportState.Failed("Something went wrong reading the file: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun confirmImport(title: String, code: String) {
        val preview = importState as? ImportState.Preview ?: return
        importState = ImportState.Saving
        viewModelScope.launch {
            try {
                val t = withContext(Dispatchers.IO) { library.add(preview.bible, title.trim(), code.trim().uppercase()) }
                store.setTranslation(t)
                importState = null
            } catch (e: Exception) {
                importState = ImportState.Failed("Couldn't save the translation: ${e.message}")
            }
        }
    }

    fun dismissImport() { importState = null }

    fun deleteTranslation(t: Translation) {
        if (progress.value.translation.id == t.id) store.setTranslation(Translation.BSB)
        viewModelScope.launch(Dispatchers.IO) { library.delete(t) }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    fun setTextScale(scale: Float) = store.setTextScale(scale)
    fun setReminder(enabled: Boolean, minuteOfDay: Int) = store.setReminder(enabled, minuteOfDay)
    fun sendTestReminder() = Reminders.show(getApplication(), force = true)

    /** From the reminder notification: go straight to today's reading. */
    fun openToday() {
        goHome(Tab.Path)
        val current = progress.value.currentDay(plan.days.size)
        if (current < plan.days.size) push(Screen.Lesson(current))
    }

    fun setThemeMode(mode: com.example.bible.ui.theme.ThemeMode) = store.setThemeMode(mode)
    fun resetProgress() = store.reset()
}
