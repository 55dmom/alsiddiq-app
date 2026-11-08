@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alsiddiq.app.BuildConfig
import com.alsiddiq.app.domain.AboutButton
import com.alsiddiq.app.domain.AboutText
import com.alsiddiq.app.ui.components.AppCard
import com.alsiddiq.app.ui.components.ScreenTopBar
import com.alsiddiq.app.ui.theme.AlSiddiqTheme
import com.alsiddiq.app.ui.theme.Brand

/**
 * «تواصل مع المطور». PLACEHOLDER values until السالمي sends the developer's WhatsApp number and email:
 * fill [WHATSAPP] / [EMAIL] (and [NAME]) here — the buttons appear by themselves once a value is set.
 */
object DeveloperContact {
    const val NAME = "المطور"
    /** PLACEHOLDER: the developer's WhatsApp number with the country code, e.g. "+9677XXXXXXXX". */
    const val WHATSAPP = ""
    /** PLACEHOLDER: the developer's email. */
    const val EMAIL = ""
    val ready get() = WHATSAPP.isNotBlank() || EMAIL.isNotBlank()
}

class DeveloperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.alsiddiq.app.ui.theme.AppThemes.load(this)
        setContent { AlSiddiqTheme { DeveloperScreen(onBack = { finish() }) } }
    }
    companion object {
        fun start(ctx: Context) = ctx.startActivity(Intent(ctx, DeveloperActivity::class.java).addFlags(if (ctx is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun DeveloperScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Brand.Bg).systemBarsPadding()) {
        ScreenTopBar("تواصل مع المطور", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Code, null, tint = Brand.Navy) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(DeveloperContact.NAME, style = MaterialTheme.typography.titleMedium, color = Brand.Navy)
                        Text("لأي ملاحظة أو اقتراح أو مشكلة في التطبيق", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
            }
            if (DeveloperContact.WHATSAPP.isNotBlank()) ContactButton(AboutButton("wa", "واتساب المطور", "whatsapp", DeveloperContact.WHATSAPP)) {
                AboutText.uri("whatsapp", DeveloperContact.WHATSAPP).let { u -> openUri(ctx, u + "?text=" + android.net.Uri.encode("السلام عليكم، بخصوص تطبيق مركز الصديق (إصدار ${BuildConfig.VERSION_NAME}): ")) }
            }
            if (DeveloperContact.EMAIL.isNotBlank()) ContactButton(AboutButton("em", "إيميل المطور", "email", DeveloperContact.EMAIL)) {
                openUri(ctx, "mailto:${DeveloperContact.EMAIL}?subject=" + android.net.Uri.encode("تطبيق مركز الصديق ${BuildConfig.VERSION_NAME}"))
            }
            if (!DeveloperContact.ready) Text("بيانات التواصل تُضاف قريباً.", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("إصدار التطبيق ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** «عن المركز» and «تواصل مع المطور» rows — in «الأدوات» and in the settings of every section. */
@Composable
fun CenterInfoEntries(onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("عن المركز", "تعريف بالمركز وعنوانه وأرقام التواصل", Icons.Outlined.Info) { onOpen(); AboutActivity.start(ctx) }
        InfoRow("تواصل مع المطور", "ملاحظة أو اقتراح أو مشكلة في التطبيق", Icons.Outlined.Code) { onOpen(); DeveloperActivity.start(ctx) }
    }
}

@Composable
private fun InfoRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Brand.Bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Navy.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Brand.Navy) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Icon(Icons.Outlined.ChevronLeft, null, tint = Brand.Muted)
        }
    }
}
