package com.alsiddiq.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alsiddiq.app.AlSiddiqApp
import com.alsiddiq.app.MainActivity
import com.alsiddiq.app.R

/** Checks the central database for new notifications and shows them as normal Android notifications. */
class NotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as AlSiddiqApp).container.cloud ?: return Result.success()
        if (!repo.isSignedIn()) return Result.success()
        val fresh = runCatching { repo.fetchNewNotifications() }.getOrElse { return Result.retry() }
        // v3.6: the admin's «تنبيهات المعلمين» — checked when this runs (app open / sync), not a push.
        val events = runCatching { repo.fetchNewAdminEvents() }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nm = NotificationManagerCompat.from(applicationContext)
        fresh.take(5).forEach { n ->
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF282564.toInt())
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(n.id.hashCode(), notif) } catch (_: SecurityException) {}
        }
        events.take(5).forEach { e ->
            val text = com.alsiddiq.app.ui.screens.adminEventText(e)
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF282564.toInt())
                .setContentTitle("تنبيه من المعلمين")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(("ev" + e.id).hashCode(), notif) } catch (_: SecurityException) {}
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL = "center_updates"
        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CHANNEL, "تحديثات المركز", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "الحفظ والحضور والإعلانات والمكافآت"
                ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
            }
        }
    }
}
