package com.alsiddiq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alsiddiq.app.ui.AppRoot
import com.alsiddiq.app.ui.theme.AlSiddiqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AlSiddiqApp).container
        // Debug-only hooks used by CI to open a screen directly for screenshots.
        val autoLogin = if (BuildConfig.DEBUG) intent?.getStringExtra("demo_login") else null
        val startRoute = if (BuildConfig.DEBUG) intent?.getStringExtra("route") else null
        val ciLogin = if (BuildConfig.DEBUG) intent?.getStringExtra("login_phone")?.let { it to (intent?.getStringExtra("login_pass") ?: "") } else null
        val selfTest = BuildConfig.DEBUG && intent?.getBooleanExtra("selftest", false) == true
        setContent {
            AlSiddiqTheme {
                AppRoot(container, autoLogin, startRoute, ciLogin, selfTest)
            }
        }
    }
}
