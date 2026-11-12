package com.example.bible.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.bible.MainActivity
import com.example.bible.R

/**
 * Keeps narration running with the screen locked or the app in the background, and shows
 * play/pause/skip controls on the lock screen and in the notification shade.
 * Runs exactly while [Narrator.active].
 */
class NarrationService : Service() {
    private lateinit var narrator: Narrator
    private lateinit var session: MediaSessionCompat
    private lateinit var wakeLock: PowerManager.WakeLock
    private var foreground = false

    // Headphones unplugged / Bluetooth dropped: pause rather than blare from the speaker.
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = narrator.pause()
    }

    override fun onCreate() {
        super.onCreate()
        narrator = Narrator.get(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bible:narration")
            .apply { setReferenceCounted(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reading aloud", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        session = MediaSessionCompat(this, "BibleNarration").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = narrator.resume()
                override fun onPause() = narrator.pause()
                override fun onStop() = narrator.stop()
                override fun onSkipToNext() = narrator.next()
                override fun onSkipToPrevious() = narrator.previous()
            })
            setSessionActivity(openApp())
            isActive = true
        }
        ContextCompat.registerReceiver(
            this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        narrator.onChange = ::update
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> narrator.togglePause()
            ACTION_NEXT -> narrator.next()
            ACTION_PREVIOUS -> narrator.previous()
            ACTION_STOP -> narrator.stop()
        }
        update()
        return START_NOT_STICKY
    }

    private fun update() {
        if (!narrator.active) {
            // Started via startForegroundService: must go foreground once, even just to stop.
            if (!foreground) ServiceCompat.startForeground(
                this, NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setSilent(true).build(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            if (wakeLock.isHeld) wakeLock.release()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        // Keep the CPU up between verses while the screen is off.
        if (narrator.playing) wakeLock.acquire(60 * 60 * 1000L) else if (wakeLock.isHeld) wakeLock.release()

        val canPrev = narrator.index > 0
        val canNext = narrator.index < narrator.playlist.lastIndex
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP or
            (if (canPrev) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0) or
            (if (canNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0)
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (narrator.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f,
                )
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, narrator.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, narrator.subtitle)
                .build(),
        )

        val compact = mutableListOf<Int>()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(narrator.title)
            .setContentText(narrator.subtitle)
            .setContentIntent(openApp())
            .setDeleteIntent(command(ACTION_STOP))
            .setOngoing(narrator.playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        var n = 0
        if (canPrev) { builder.addAction(android.R.drawable.ic_media_previous, "Previous chapter", command(ACTION_PREVIOUS)); compact += n++ }
        builder.addAction(
            if (narrator.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (narrator.playing) "Pause" else "Play",
            command(ACTION_TOGGLE),
        ); compact += n++
        if (canNext) { builder.addAction(android.R.drawable.ic_media_next, "Next chapter", command(ACTION_NEXT)); compact += n++ }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", command(ACTION_STOP))
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(*compact.toIntArray()),
        )
        val notification: Notification = builder.build()

        if (!foreground) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, NarrationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        narrator.onChange = null
        unregisterReceiver(noisy)
        if (wakeLock.isHeld) wakeLock.release()
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "narration"
        const val NOTIFICATION_ID = 2
        const val ACTION_TOGGLE = "com.example.bible.narration.TOGGLE"
        const val ACTION_NEXT = "com.example.bible.narration.NEXT"
        const val ACTION_PREVIOUS = "com.example.bible.narration.PREVIOUS"
        const val ACTION_STOP = "com.example.bible.narration.STOP"
    }
}
