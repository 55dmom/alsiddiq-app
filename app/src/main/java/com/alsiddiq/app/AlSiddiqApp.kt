package com.alsiddiq.app

import android.app.Application
import com.alsiddiq.app.data.AuthRepository
import com.alsiddiq.app.data.CenterRepository
import com.alsiddiq.app.data.demo.DemoRepository

/** Manual dependency container. Picks the backend once at start-up. */
class AppContainer(app: Application) {
    // The cloud backend is plugged in here once it is configured (BuildConfig.SUPABASE_URL set).
    // Until then the app runs on the local demo store and says so on screen.
    private val demo by lazy { DemoRepository(app) }
    val auth: AuthRepository get() = demo
    val center: CenterRepository get() = demo
    val cloudConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}

class AlSiddiqApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
