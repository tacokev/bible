package com.example.bible

import com.example.bible.data.Streaks
import com.example.bible.data.dayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    @Test fun emptyIsZero() = assertEquals(0, Streaks.current(emptySet(), 100))

    @Test fun countsRunEndingToday() = assertEquals(3, Streaks.current(setOf(98L, 99L, 100L), 100))

    @Test fun yesterdayStillCountsBeforeReadingToday() = assertEquals(2, Streaks.current(setOf(98L, 99L), 100))

    @Test fun gapBreaksStreak() = assertEquals(0, Streaks.current(setOf(97L, 98L), 100))

    @Test fun onlyLatestRunCounts() = assertEquals(1, Streaks.current(setOf(90L, 91L, 92L, 100L), 100))

    @Test fun epochWasThursday() = assertEquals(4, dayOfWeek(0))
}
