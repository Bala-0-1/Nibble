package com.nibble.app.core.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nibble.app.R

/**
 * Foreground media service (§8) that owns the TTS engine, a MediaSession, and an ongoing
 * media-style notification with lockscreen/Bluetooth transport controls — so narration
 * keeps playing when the app is backgrounded. State flows back through [NarrationBus].
 */
class NarrationService : Service() {

    companion object {
        const val ACTION_SPEAK = "com.nibble.app.audio.SPEAK"
        const val ACTION_PAUSE = "com.nibble.app.audio.PAUSE"
        const val ACTION_RESUME = "com.nibble.app.audio.RESUME"
        const val ACTION_STOP = "com.nibble.app.audio.STOP"
        const val EX_TEXT = "text"
        const val EX_FROM = "from"
        const val EX_RATE = "rate"
        const val EX_TITLE = "title"
        const val EX_BOOK = "book"
        private const val CHANNEL = "playback"
        private const val NOTIF = 2001
        private const val CLAY = 0xFFA85732.toInt()
    }

    private var tts: TextToSpeech? = null
    private lateinit var session: MediaSessionCompat
    private var ready = false
    private var fullText = ""
    private var baseOffset = 0
    private var rate = 1f
    private var title = "Nibble"
    private var bookId = ""
    private var pending: (() -> Unit)? = null
    /** Absolute start offset of each queued utterance chunk (TTS caps one utterance ~4k chars). */
    private var chunkStarts: List<Int> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "nibble").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopNarration()
            })
            isActive = true
        }
        tts = TextToSpeech(applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    private fun chunkOf(id: String?): Int = id?.removePrefix("nibble-")?.toIntOrNull() ?: 0
                    override fun onStart(id: String?) { NarrationBus.state.value = NarratorState.SPEAKING }
                    override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                        val base = chunkStarts.getOrNull(chunkOf(id)) ?: baseOffset
                        NarrationBus.spokenChar.value = base + start
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) = finishAll()
                    override fun onDone(id: String?) {
                        // Chunks are queued; the chapter is only done after the LAST one.
                        if (chunkOf(id) >= chunkStarts.lastIndex) finishAll()
                    }
                })
                pending?.invoke(); pending = null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SPEAK -> {
                fullText = intent.getStringExtra(EX_TEXT).orEmpty()
                baseOffset = intent.getIntExtra(EX_FROM, 0)
                rate = intent.getFloatExtra(EX_RATE, 1f)
                title = intent.getStringExtra(EX_TITLE) ?: "Nibble"
                bookId = intent.getStringExtra(EX_BOOK).orEmpty()
                NarrationBus.spokenChar.value = baseOffset
                startForeground(NOTIF, buildNotification(true))
                if (ready) startFrom(baseOffset) else pending = { startFrom(baseOffset) }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopNarration()
        }
        return START_NOT_STICKY
    }

    private fun startFrom(from: Int) {
        baseOffset = from.coerceIn(0, fullText.length)
        NarrationBus.spokenChar.value = baseOffset
        NarrationBus.state.value = NarratorState.SPEAKING
        updatePlaybackState(true)
        startForeground(NOTIF, buildNotification(true))
        tts?.setSpeechRate(rate)
        // One utterance per ≲3.5k chars, broken at sentence ends — the engine rejects
        // anything over getMaxSpeechInputLength (a whole chapter always is).
        chunkStarts = chunkBoundaries(baseOffset)
        chunkStarts.forEachIndexed { i, s ->
            val e = if (i + 1 < chunkStarts.size) chunkStarts[i + 1] else fullText.length
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(fullText.substring(s, e), mode, Bundle(), "nibble-$i")
        }
    }

    private fun chunkBoundaries(from: Int): List<Int> {
        val cap = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtMost(3500)
        val starts = ArrayList<Int>()
        var pos = from
        while (pos < fullText.length) {
            starts.add(pos)
            var end = (pos + cap).coerceAtMost(fullText.length)
            if (end < fullText.length) {
                val slice = fullText.substring(pos, end)
                val sentenceCut = maxOf(
                    slice.lastIndexOf(". "), slice.lastIndexOf("! "),
                    slice.lastIndexOf("? "), slice.lastIndexOf('\n'),
                )
                end = when {
                    sentenceCut > cap / 2 -> pos + sentenceCut + 1
                    else -> {
                        val space = slice.lastIndexOf(' ')
                        if (space > 0) pos + space + 1 else end
                    }
                }
            }
            pos = end
        }
        if (starts.isEmpty()) starts.add(from)
        return starts
    }

    private fun pause() {
        tts?.stop()
        NarrationBus.state.value = NarratorState.PAUSED
        updatePlaybackState(false)
        NotificationManagerCompat.from(this).notify(NOTIF, buildNotification(false))
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun resume() = startFrom(NarrationBus.spokenChar.value)

    private fun stopNarration() {
        tts?.stop()
        NarrationBus.state.value = NarratorState.IDLE
        updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishAll() {
        NarrationBus.state.value = NarratorState.IDLE
        updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updatePlaybackState(playing: Boolean) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f,
                ).build(),
        )
    }

    private fun servicePending(action: String, req: Int): PendingIntent {
        val i = Intent(this, NarrationService::class.java).setAction(action)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, req, i, flags)
    }

    private fun openReaderPending(): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("nibble://read/$bookId?mode=audio")).setPackage(packageName)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 10, i, flags)
    }

    private fun buildNotification(playing: Boolean): android.app.Notification {
        val toggle = if (playing) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", servicePending(ACTION_PAUSE, 1))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", servicePending(ACTION_RESUME, 2))
        }
        val stop = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePending(ACTION_STOP, 3))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (playing) "Narrating · tap to open" else "Paused")
            .setContentIntent(openReaderPending())
            .setColor(CLAY)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Narration", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        session.release()
        super.onDestroy()
    }
}
