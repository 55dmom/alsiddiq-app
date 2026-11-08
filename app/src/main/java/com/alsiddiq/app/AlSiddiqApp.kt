package com.alsiddiq.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alsiddiq.app.data.AuthRepository
import com.alsiddiq.app.data.CenterRepository
import com.alsiddiq.app.data.cloud.CloudRepository
import com.alsiddiq.app.data.demo.DemoRepository
import com.alsiddiq.app.notify.NotificationWorker
import java.util.concurrent.TimeUnit

/** Manual dependency container. Picks the backend once at start-up. */
class AppContainer(private val app: Application) {
    /** The central database is used whenever this build carries its address and public key. */
    val cloudConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val cloud: CloudRepository? by lazy {
        if (cloudConfigured) CloudRepository(app, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) else null
    }
    private val demo by lazy { DemoRepository(app) }

    val auth: AuthRepository get() = cloud ?: demo
    val center: CenterRepository get() = cloud ?: demo
}

class AlSiddiqApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationWorker.createChannel(this)
        if (container.cloudConfigured) {
            val req = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("alsiddiq-notifications", ExistingPeriodicWorkPolicy.KEEP, req)
            com.alsiddiq.app.notify.SyncWorker.schedule(this)   // v3.2: waiting changes go up on open and whenever the network is back
        }
    }
}
