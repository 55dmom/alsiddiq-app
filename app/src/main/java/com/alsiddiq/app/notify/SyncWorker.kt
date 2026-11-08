package com.alsiddiq.app.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alsiddiq.app.AlSiddiqApp
import java.util.concurrent.TimeUnit

/**
 * v3.2 offline sync: sends the changes waiting on the phone as soon as there is a network
 * (WorkManager runs it only when CONNECTED, even if the app is closed), and on every app open.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as AlSiddiqApp).container.cloud ?: return Result.success()
        if (!repo.hasWaiting() && !repo.isSignedIn()) return Result.success()
        return if (repo.backgroundSync()) Result.success() else Result.retry()
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes when online, plus a one-off now (it waits for the network if there is none). */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork("alsiddiq-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())
            kick(ctx)
        }

        fun kick(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("alsiddiq-sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
