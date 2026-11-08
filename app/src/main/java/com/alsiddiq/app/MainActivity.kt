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
        // CI against the real database: login_kind = viewer | admin | teacher, login_pass = that password,
        // viewer_pass = the general password kept underneath, try_student = "studentId:circleId" for raw write probes.
        val ciLogin = if (BuildConfig.DEBUG) intent?.getStringExtra("login_pass")?.let { (intent?.getStringExtra("login_kind") ?: "viewer") to it } else null
        val ciViewer = if (BuildConfig.DEBUG) intent?.getStringExtra("viewer_pass") else null
        val ciTry = if (BuildConfig.DEBUG) intent?.getStringExtra("try_student") else null
        val selfTest = BuildConfig.DEBUG && intent?.getBooleanExtra("selftest", false) == true
        val photoTest = BuildConfig.DEBUG && intent?.getBooleanExtra("photo_test", false) == true
        // CI screenshots of his side buttons: open_tool = prayer | tasbeeh | asma | adhkar | duas | menu
        if (BuildConfig.DEBUG) when (intent?.getStringExtra("open_tool")) {
            "prayer" -> com.alsalmi.buttons.prayer.PrayerTimesActivity.start(this)
            "tasbeeh" -> com.alsalmi.buttons.tasbeeh.TasbeehActivity.start(this)
            "asma" -> com.alsalmi.buttons.asma.AsmaAllahActivity.start(this)
            "adhkar" -> com.alsalmi.buttons.adhkar.AdhkarActivity.start(this)
            "duas" -> com.alsalmi.buttons.duas.DuasActivity.start(this)
            "menu" -> com.alsiddiq.app.ui.components.Tools.openOnStart = true
        }
        setContent {
            AlSiddiqTheme {
                AppRoot(container, autoLogin, startRoute, ciLogin, selfTest, ciViewer, ciTry, photoTest)
            }
        }
    }
}
