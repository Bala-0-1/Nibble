package com.nibble.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nibble.app.core.audio.AndroidNarrator
import com.nibble.app.core.data.AndroidEpub
import com.nibble.app.core.data.AndroidPersistence
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.Notifier
import com.nibble.app.core.data.StreakWorker
import com.nibble.app.core.data.dripSnapshot
import com.nibble.app.core.domain.EpubParser
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var narrator: AndroidNarrator
    private val persistence by lazy { AndroidPersistence(this) }
    private var handle: AppHandle? = null
    private var pendingDeepLink: Uri? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) postDripNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        narrator = AndroidNarrator(this)
        Notifier.ensureChannel(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        scheduleStreakWork()
        captureDeepLink(intent)
        setContent {
            App(
                narrator = narrator,
                persistence = persistence,
                onImportEpub = { picker.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*")) },
                onPostDrip = { drip() },
                onShareText = { text ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    startActivity(Intent.createChooser(send, null))
                },
                onReaderActive = { active ->
                    // A book outlasts the screen timeout; release the hold the moment we leave.
                    if (active) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                },
                reduceMotion = reduceMotion(),
                onReady = { handle = it; applyDeepLink() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
        applyDeepLink()
    }

    // ---- deep links (§5) ----

    private fun captureDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "nibble") pendingDeepLink = intent.data
    }

    private fun applyDeepLink() {
        val store = handle?.store ?: return
        val uri = pendingDeepLink ?: return
        pendingDeepLink = null
        when (uri.host) {
            "read" -> uri.pathSegments.firstOrNull()?.let { bookId ->
                store.onEvent(Event.OpenReader(bookId))
                if (uri.getQueryParameter("mode") == "audio") store.onEvent(Event.HandoffToAudio)
            }
        }
    }

    // ---- notifications (§9) ----

    private fun drip() {
        if (Notifier.hasPermission(this)) postDripNow()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun postDripNow() {
        val snap = handle?.store?.state?.value?.dripSnapshot() ?: return
        Notifier.postDrip(applicationContext, snap)
    }

    private fun reduceMotion(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(
            contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }.getOrDefault(false)

    private fun scheduleStreakWork() {
        val request = PeriodicWorkRequestBuilder<StreakWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("nibble-streak", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // ---- import (§10) ----

    private fun importUri(uri: Uri) {
        val store = handle?.store ?: return
        Thread {
            try {
                val book = AndroidEpub.read(applicationContext, uri)
                runOnUiThread { store.onEvent(Event.ImportBook(book)) }
            } catch (d: EpubParser.DrmException) {
                runOnUiThread { store.onEvent(Event.ImportFailed(d.message ?: "This file is copy-protected.")) }
            } catch (e: Exception) {
                runOnUiThread { store.onEvent(Event.ImportFailed("Couldn't open this file. Make sure it's an unprotected EPUB.")) }
            }
        }.start()
    }

    override fun onStop() {
        handle?.store?.saveNow()
        super.onStop()
    }

    override fun onDestroy() {
        narrator.release()
        super.onDestroy()
    }
}
