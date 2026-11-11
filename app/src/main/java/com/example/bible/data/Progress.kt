package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.bible.ui.theme.ThemeMode
import java.util.TimeZone

/** Local calendar day number (days since 1970-01-01 in the device time zone). */
fun today(nowMillis: Long = System.currentTimeMillis()): Long {
    val offset = TimeZone.getDefault().getOffset(nowMillis)
    return Math.floorDiv(nowMillis + offset, 86_400_000L)
}

/** 0 = Sunday … 6 = Saturday. 1970-01-01 was a Thursday. */
fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 4, 7L).toInt()

data class Progress(
    val completedDays: Set<Int> = emptySet(),
    val activeDates: Set<Long> = emptySet(),
    val longestStreak: Int = 0,
    val translation: Translation = Translation.BSB,
    val textScale: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.System,
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight, e.g. 480 = 8:00 AM. */
    val reminderMinute: Int = 8 * 60,
) {
    fun streak(on: Long = today()): Int = Streaks.current(activeDates, on)
    fun readToday(on: Long = today()) = on in activeDates

    /** The first plan day not yet completed (the "current lesson"). */
    fun currentDay(totalDays: Int): Int =
        (0 until totalDays).firstOrNull { it !in completedDays } ?: totalDays
}

object Streaks {
    /** Consecutive active days ending today, or ending yesterday if today isn't done yet. */
    fun current(active: Set<Long>, today: Long): Int {
        var day = if (today in active) today else today - 1
        var count = 0
        while (day in active) { count++; day-- }
        return count
    }
}

data class CompletionResult(
    val dayIndex: Int,
    val chapters: Int,
    val streakBefore: Int,
    val streakAfter: Int,
    val extendedStreakToday: Boolean,
    val daysCompleted: Int,
    val activeDates: Set<Long>,
)

class ProgressStore(context: Context, private val library: TranslationLibrary) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Progress> = _state.asStateFlow()

    private fun read() = Progress(
        completedDays = prefs.getStringSet("completed", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet(),
        activeDates = prefs.getStringSet("active", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet(),
        longestStreak = prefs.getInt("longest", 0),
        translation = library.find(prefs.getString("translation", null)) ?: Translation.BSB,
        textScale = prefs.getFloat("textScale", 1f),
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.System,
        reminderEnabled = prefs.getBoolean("reminder", false),
        reminderMinute = prefs.getInt("reminderMinute", 8 * 60),
    )

    private fun write(p: Progress) {
        prefs.edit()
            .putStringSet("completed", p.completedDays.map { it.toString() }.toSet())
            .putStringSet("active", p.activeDates.map { it.toString() }.toSet())
            .putInt("longest", p.longestStreak)
            .putString("translation", p.translation.id)
            .putFloat("textScale", p.textScale)
            .putString("theme", p.themeMode.name)
            .putBoolean("reminder", p.reminderEnabled)
            .putInt("reminderMinute", p.reminderMinute)
            .apply()
        _state.value = p
    }

    fun completeDay(dayIndex: Int, chapters: Int, on: Long = today()): CompletionResult {
        val p = _state.value
        val before = p.streak(on)
        val active = p.activeDates + on
        val after = Streaks.current(active, on)
        val completed = p.completedDays + dayIndex
        write(
            p.copy(
                completedDays = completed,
                activeDates = active,
                longestStreak = maxOf(p.longestStreak, after),
            )
        )
        Reminders.clear(appContext) // done for today; drop any reminder still showing
        return CompletionResult(dayIndex, chapters, before, after, on !in p.activeDates, completed.size, active)
    }

    fun setTranslation(t: Translation) = write(_state.value.copy(translation = t))
    fun setTextScale(scale: Float) = write(_state.value.copy(textScale = scale.coerceIn(0.8f, 1.6f)))
    fun setThemeMode(mode: ThemeMode) = write(_state.value.copy(themeMode = mode))

    fun setReminder(enabled: Boolean, minuteOfDay: Int) {
        write(_state.value.copy(reminderEnabled = enabled, reminderMinute = minuteOfDay))
        Reminders.schedule(appContext, enabled, minuteOfDay)
    }
    fun reset() = _state.value.let {
        write(Progress(translation = it.translation, textScale = it.textScale, themeMode = it.themeMode, reminderEnabled = it.reminderEnabled, reminderMinute = it.reminderMinute))
    }
}
