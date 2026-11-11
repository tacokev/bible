package com.example.bible.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bible.MainActivity
import com.example.bible.R
import java.util.Calendar

/**
 * Daily reading reminder. One alarm a day at the chosen time; when it fires we only post a
 * notification if today's reading isn't done yet, then schedule tomorrow's.
 */
object Reminders {
    const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001
    const val ACTION_FIRE = "com.example.bible.REMINDER"
    const val EXTRA_OPEN_TODAY = "open_today"

    /** Next time (millis) the clock shows [minuteOfDay], strictly after [now]. */
    fun nextTrigger(now: Long, minuteOfDay: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun formatTime(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }

    /** Title and body for the reminder, given the streak and today's reading summary (null if the plan is finished). */
    fun message(streak: Int, dayNumber: Int?, summary: String?): Pair<String, String> {
        val reading = if (dayNumber != null && summary != null) "Day $dayNumber: $summary" else "Spend a few minutes in the Word today."
        val title = when {
            streak >= 2 -> "Keep your $streak-day streak alive 🔥"
            streak == 1 -> "Make it 2 days in a row 🔥"
            else -> "Time for today's reading 📖"
        }
        return title to reading
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedules (or cancels) the next reminder. Inexact within a few minutes, which needs no special permission. */
    fun schedule(context: Context, enabled: Boolean, minuteOfDay: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        if (!enabled) return
        val at = nextTrigger(System.currentTimeMillis(), minuteOfDay)
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily reading reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A daily nudge to keep your reading streak going"
                }
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts the reminder. With [force] it's sent even if today's reading is already done (for "Send a test"). */
    fun show(context: Context, force: Boolean = false) {
        val store = ProgressStore(context, TranslationLibrary(context))
        val p = store.state.value
        if (!force && p.readToday()) return
        if (!canNotify(context)) return
        ensureChannel(context)

        val plan = ReadingPlan.load(context)
        val current = p.currentDay(plan.days.size)
        val day = plan.days.getOrNull(current)
        val (title, body) = message(p.streak(), day?.dayNumber, day?.summary)

        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TODAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF58CC02.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, "Start reading", open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and now; nothing to do.
        }
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

/** Fires the reminder and reschedules it after reboots, app updates and clock/time-zone changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = ProgressStore(context, TranslationLibrary(context)).state.value
        if (intent.action == Reminders.ACTION_FIRE) Reminders.show(context)
        Reminders.schedule(context, p.reminderEnabled, p.reminderMinute)
    }
}
