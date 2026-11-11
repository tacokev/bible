package com.example.bible

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bible.data.ProgressStore
import com.example.bible.data.Reminders
import com.example.bible.data.TranslationLibrary
import com.example.bible.data.today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemindersTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun at(h: Int, m: Int) = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 11, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun laterToday() = assertEquals(at(20, 0), Reminders.nextTrigger(at(9, 30), 20 * 60))

    @Test fun earlierTimeMeansTomorrow() {
        val next = Calendar.getInstance().apply { timeInMillis = Reminders.nextTrigger(at(9, 30), 8 * 60) }
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun exactlyNowMeansTomorrow() =
        assertEquals(at(8, 0) + 86_400_000L, Reminders.nextTrigger(at(8, 0), 8 * 60))

    @Test fun formatTime() {
        assertEquals("8:00 AM", Reminders.formatTime(480))
        assertEquals("12:05 PM", Reminders.formatTime(725))
        assertEquals("12:00 AM", Reminders.formatTime(0))
        assertEquals("9:30 PM", Reminders.formatTime(21 * 60 + 30))
    }

    @Test fun messages() {
        assertEquals("Keep your 5-day streak alive 🔥", Reminders.message(5, 13, "Genesis 27").first)
        assertEquals("Day 13: Genesis 27", Reminders.message(5, 13, "Genesis 27").second)
        assertEquals("Make it 2 days in a row 🔥", Reminders.message(1, 2, "x").first)
        assertEquals("Time for today's reading 📖", Reminders.message(0, 1, "x").first)
        assertTrue(Reminders.message(0, null, null).second.isNotBlank())
    }

    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @Test fun postsWhenNotReadToday() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Reminders.show(app)
        val n = shadowOf(nm).allNotifications.single()
        assertEquals("Time for today's reading 📖", shadowOf(n).contentTitle)
        assertEquals("Day 1: Genesis 1–2 · Matthew 1 · Psalms 1", shadowOf(n).contentText)
    }

    @Test fun silentWhenAlreadyReadToday() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("progress", Context.MODE_PRIVATE).edit()
            .putStringSet("active", setOf("${today()}")).commit()
        Reminders.show(app)
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun silentWithoutPermission() {
        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Reminders.show(app)
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun enablingSchedulesAlarmAndDisablingCancels() {
        val store = ProgressStore(app, TranslationLibrary(app))
        val am = shadowOf(app.getSystemService(AlarmManager::class.java))
        store.setReminder(true, 20 * 60)
        assertNotNull(am.nextScheduledAlarm)
        store.setReminder(false, 20 * 60)
        assertNull(am.nextScheduledAlarm)
    }
}
