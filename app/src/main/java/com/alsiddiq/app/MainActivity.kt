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
        // «الثيمات»: CI can preselect a theme for screenshots (debug only); otherwise the saved choice.
        if (BuildConfig.DEBUG) intent?.getStringExtra("theme")?.let { com.alsiddiq.app.ui.theme.AppThemes.select(this, com.alsiddiq.app.ui.theme.AppThemes.byId(it)) }
        if (BuildConfig.DEBUG) intent?.getStringExtra("sim_date")?.let { com.alsiddiq.app.ui.theme.Occasions.simulate = it }
        com.alsiddiq.app.ui.theme.AppThemes.load(this)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("open_themes", false) == true) com.alsiddiq.app.ui.components.ThemesUi.openOnStart = true
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
        // CI screenshots of his side buttons: open_tool = mushaf | mushaf_page (+ page) | hifz | khatma | prayer | tasbeeh | asma | adhkar | duas | menu
        if (BuildConfig.DEBUG) when (intent?.getStringExtra("open_tool")) {
            "mushaf" -> com.alsalmi.buttons.mushaf.MushafActivity.start(this)
            "hifz" -> com.alsalmi.buttons.hifz.HifzActivity.start(this)
            "khatma" -> com.alsalmi.buttons.khatma.KhatmaActivity.start(this)
            "prayer" -> com.alsalmi.buttons.prayer.PrayerTimesActivity.start(this)
            "tasbeeh" -> com.alsalmi.buttons.tasbeeh.TasbeehActivity.start(this)
            "asma" -> com.alsalmi.buttons.asma.AsmaAllahActivity.start(this)
            "adhkar" -> com.alsalmi.buttons.adhkar.AdhkarActivity.start(this)
            "duas" -> com.alsalmi.buttons.duas.DuasActivity.start(this)
            "menu" -> com.alsiddiq.app.ui.components.Tools.openOnStart = true
            "about" -> com.alsiddiq.app.ui.about.AboutActivity.start(this)
            "about_edit" -> com.alsiddiq.app.ui.about.AboutActivity.start(this, edit = true)
            "developer" -> com.alsalmi.buttons.contact.ContactDeveloperActivity.start(this)
        }
        // CI screenshots of v3.2's sync line in the demo build (simulated outbox; the real one needs the central database).
        if (BuildConfig.DEBUG) intent?.getStringExtra("sim_pending")?.toIntOrNull()?.let { com.alsiddiq.app.ui.screens.SyncSim.pending = it }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("sim_reauth", false) == true) com.alsiddiq.app.ui.screens.SyncSim.needsPassword = true
        if (BuildConfig.DEBUG) intent?.getStringExtra("sim_keys")?.let { com.alsiddiq.app.ui.screens.SyncSim.pendingKeys = it.split(",").map { k -> k.trim() }.toSet() }
        // v3.2: every app open sends what is waiting (WorkManager waits for the network if there is none).
        if (container.cloudConfigured) runCatching { com.alsiddiq.app.notify.SyncWorker.kick(this) }
        if (BuildConfig.DEBUG && intent?.getStringExtra("open_tool") == "mushaf_page") {
            val page = intent?.getStringExtra("page")?.toIntOrNull() ?: 1
            setContent { com.alsalmi.buttons.mushaf.MushafScreen(onBack = { finish() }, startPage = page) }
            return
        }
        setContent {
            AlSiddiqTheme {
                AppRoot(container, autoLogin, startRoute, ciLogin, selfTest, ciViewer, ciTry, photoTest)
                com.alsiddiq.app.ui.components.OccasionPopupHost()
            }
        }
    }
}
