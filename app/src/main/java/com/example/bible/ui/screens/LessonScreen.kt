package com.example.bible.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import com.example.bible.AppViewModel
import com.example.bible.data.Block
import com.example.bible.data.Chapter
import com.example.bible.data.Narrator
import com.example.bible.ui.components.NarrationLifecycle
import com.example.bible.ui.components.SpeakButton
import com.example.bible.data.Progress
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.ImmersiveScaffold
import com.example.bible.ui.components.PopProgressBar
import com.example.bible.ui.components.chapterItems
import com.example.bible.ui.components.NoteDialog
import com.example.bible.ui.components.VerseActions
import com.example.bible.ui.components.VerseInteraction
import com.example.bible.ui.components.VerseMenu
import com.example.bible.ui.components.rememberVerseActions
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.example.bible.ui.components.popIn
import com.example.bible.ui.theme.Palette

@Composable
fun LessonScreen(vm: AppViewModel, progress: Progress, dayIndex: Int) {
    val day = vm.plan.days[dayIndex]
    val chapters = day.chapters
    var index by rememberSaveable { mutableStateOf(0) }
    var confirmQuit by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (index > 0) index-- else confirmQuit = true
    }

    val last = index == chapters.lastIndex
    val narrator = vm.narrator
    NarrationLifecycle(narrator)
    // Reading aloud runs through the day's chapters by itself (even with the screen locked);
    // the page follows it, and Continue/back move it along.
    val playlistId = "lesson:$dayIndex"
    val narrating = narrator.playlistId == playlistId
    LaunchedEffect(narrator.index, narrating) {
        if (narrating && narrator.index != index) index = narrator.index
    }
    LaunchedEffect(index) {
        if (narrating && narrator.index != index) narrator.jumpTo(index)
    }
    ImmersiveScaffold(
        resetKey = index,
        topBar = {
        // Header: close, progress bar, streak
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Close, "Close",
                Modifier
                    .size(32.dp)
                    .clickableNoRipple { confirmQuit = true },
                tint = Palette.Faint,
            )
            Spacer(Modifier.width(12.dp))
            PopProgressBar(index / chapters.size.toFloat(), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(AppIcons.Flame, null, Modifier.size(24.dp), tint = Color.Unspecified)
            Text("${progress.streak()}", style = MaterialTheme.typography.titleMedium, color = Palette.Flame)
        }
        },
        bottomBar = {
            HorizontalDivider(color = Palette.Line, thickness = 2.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeakButton(narrating && narrator.playing, Modifier.width(64.dp)) {
                    when {
                        narrator.playing -> narrator.pause()
                        narrating && narrator.index == index -> narrator.resume()
                        else -> narrator.play(
                            playlistId, chapters, index, "Day ${day.dayNumber} · ${progress.translation.title}",
                            load = vm::chapter,
                        )
                    }
                }
                PopButton(
                    text = if (last) "Finish day" else "Continue",
                    onClick = { if (last) vm.completeDay(dayIndex) else index++ },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it * dir / 3 } + fadeOut(tween(150)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "chapter",
        ) { i ->
            val (bookId, number) = chapters[i]
            val chapter by produceState<Chapter?>(null, bookId, number, progress.translation) {
                value = vm.chapter(bookId, number)
            }
            val key = Narrator.key(bookId, number)
            val actions = rememberVerseActions(vm, bookId, number) { v ->
                narrator.play(playlistId, chapters, i, "Day ${day.dayNumber} · ${progress.translation.title}", fromVerse = v, load = vm::chapter)
            }
            ChapterPage(
                actions = actions,
                speakingVerse = narrator.verse.takeIf { narrating && narrator.chapterKey == key },
                eyebrow = "DAY ${day.dayNumber} · READING ${i + 1} OF ${chapters.size}",
                chapter = chapter,
                textScale = progress.textScale,
                translationCode = progress.translation.code,
                padding = padding,
            )
        }
    }

    if (confirmQuit) {
        QuitDialog(onKeep = { confirmQuit = false }, onQuit = { confirmQuit = false; vm.pop() })
    }
}

@Composable
fun ChapterPage(
    eyebrow: String?,
    chapter: Chapter?,
    textScale: Float,
    translationCode: String,
    highlightVerse: Int? = null,
    padding: PaddingValues = PaddingValues(),
    speakingVerse: Int? = null,
    actions: VerseActions? = null,
) {
    if (chapter == null) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Green.face, strokeWidth = 5.dp)
        }
        return
    }
    if (chapter.blocks.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.BookColor, null, Modifier.size(64.dp), tint = Color.Unspecified)
            Spacer(Modifier.height(12.dp))
            Text("${chapter.book.name} ${chapter.number}", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "$translationCode doesn't include this chapter. Switch translation from the top bar to read it.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    // Long-press menu (verse + where it is on screen) and the note being edited.
    var menu by remember(chapter) { mutableStateOf<Pair<Int, Rect>?>(null) }
    var noteVerse by remember(chapter) { mutableStateOf<Int?>(null) }
    val interaction = actions?.let {
        VerseInteraction(
            marks = it.marks,
            selected = menu?.first,
            onLongPress = { v, bounds -> menu = v to bounds },
            onNoteClick = { v -> noteVerse = v },
        )
    }
    if (actions != null) {
        menu?.let { (v, bounds) ->
            VerseMenu(
                chapter = chapter, verse = v, anchor = bounds, translationCode = translationCode, actions = actions,
                onEditNote = { menu = null; noteVerse = v },
                onDismiss = { menu = null },
            )
        }
        noteVerse?.let { v ->
            NoteDialog(
                chapter = chapter, verse = v, initial = actions.marks[v]?.note,
                onSave = { actions.onNote(v, it); noteVerse = null },
                onDismiss = { noteVerse = null },
            )
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chapter) {
        // Jump to the highlighted verse's paragraph (+1 for the title item), leaving a little room above.
        val block = highlightVerse?.let { v ->
            chapter.blocks.indexOfFirst { b -> b is Block.Paragraph && b.verses.any { it.number == v } }
        } ?: -1
        if (block >= 0) listState.scrollToItem(block + 1) else listState.scrollToItem(0)
    }
    // Follow along while reading aloud: keep the spoken verse in the upper part of the screen.
    LaunchedEffect(speakingVerse) {
        val v = speakingVerse ?: return@LaunchedEffect
        val block = chapter.blocks.indexOfFirst { b -> b is Block.Paragraph && b.verses.any { it.number == v } }
        if (block < 0) return@LaunchedEffect
        val p = chapter.blocks[block] as Block.Paragraph
        // Rough position of the verse inside its paragraph, by characters.
        val before = p.verses.takeWhile { it.number != v }.sumOf { it.lines.sumOf { l -> l.length } + 4 }
        val total = p.verses.sumOf { it.lines.sumOf { l -> l.length } + 4 }.coerceAtLeast(1)
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == block + 1 }
        val visibleEnd = info.viewportEndOffset - info.afterContentPadding
        if (item == null) {
            listState.animateScrollToItem(block + 1)
        } else {
            val y = item.offset + item.size * before / total
            if (y < 0 || y > visibleEnd * 2 / 3) listState.animateScrollBy((y - visibleEnd / 5).toFloat())
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 8.dp)) {
                if (eyebrow != null) {
                    Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = Palette.Blue.face)
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${chapter.book.name} ${chapter.number}", style = MaterialTheme.typography.headlineLarge)
                    Text(translationCode, style = MaterialTheme.typography.labelMedium, color = Palette.Faint, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
        chapterItems(chapter, textScale, speakingVerse ?: highlightVerse, interaction)
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuitDialog(onKeep: () -> Unit, onQuit: () -> Unit) {
    Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier
                .popIn()
                .background(Palette.Background, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(AppIcons.Flame, null, Modifier.size(64.dp), tint = Color.Unspecified)
            Spacer(Modifier.height(12.dp))
            Text("Wait, don't go yet!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "You're almost there. If you quit now you'll lose today's progress.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PopButton("Keep reading", onKeep, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(10.dp))
            Text(
                "END SESSION",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Red.face,
                modifier = Modifier
                    .clickableNoRipple(onQuit)
                    .padding(12.dp),
            )
        }
    }
}
