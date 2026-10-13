package com.nibble.app.core.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nibble.app.R
import com.nibble.app.core.domain.DripSnapshot

/** Off-app surfaces (§9): the lockscreen drip and streak nudge, with deep-link actions. */
object Notifier {
    const val CHANNEL_DRIP = "drip"
    private const val ID_DRIP = 1001
    private const val ID_STREAK = 1002
    private const val CLAY = 0xFFA85732.toInt()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_DRIP) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_DRIP, "Reading nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Two-minute nibbles and the occasional dry reminder."
                    },
                )
            }
        }
    }

    private fun deepLink(context: Context, uri: String, req: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(context.packageName)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, req, intent, flags)
    }

    fun postDrip(context: Context, snap: DripSnapshot) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_DRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nibble · now")
            .setContentText(snap.excerpt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(snap.excerpt).setSummaryText(snap.sub))
            .setSubText(snap.sub)
            .setColor(CLAY)
            .setContentIntent(deepLink(context, "nibble://read/${snap.bookId}", 0))
            .addAction(0, "Finish it", deepLink(context, "nibble://read/${snap.bookId}", 1))
            .addAction(0, "Listen", deepLink(context, "nibble://read/${snap.bookId}?mode=audio", 2))
            .setAutoCancel(true)
            .build()
        post(context, ID_DRIP, n)
    }

    fun postStreakNudge(context: Context, bookId: String, text: String) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_DRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nibble")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(CLAY)
            .setContentIntent(deepLink(context, "nibble://read/$bookId", 3))
            .addAction(0, "Read now", deepLink(context, "nibble://read/$bookId", 4))
            .setAutoCancel(true)
            .build()
        post(context, ID_STREAK, n)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, n: Notification) {
        if (!hasPermission(context)) return
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
