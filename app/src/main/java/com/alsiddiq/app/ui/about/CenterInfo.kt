package com.alsiddiq.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alsalmi.buttons.contact.ContactDeveloperActivity
import com.alsiddiq.app.ui.theme.Brand

/**
 * «عن المركز» and «تواصل مع المطوّر» rows — in «الأدوات» (before any password) and in the settings of every section.
 * «تواصل مع المطوّر» is his own fixed module (contact): not stored anywhere, not editable by anyone.
 */
@Composable
fun CenterInfoEntries(onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("عن المركز", "تعريف بالمركز وعنوانه وأرقام التواصل", Icons.Outlined.Info, Brand.Navy) { onOpen(); AboutActivity.start(ctx) }
        DeveloperRow(onOpen)
    }
}

/** His «تواصل مع المطوّر» button (opens his ContactDeveloperActivity as given). */
@Composable
fun DeveloperRow(onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    InfoRow("تواصل مع المطوّر", "اقتراح، سؤال، أو استفسار", Icons.AutoMirrored.Outlined.Send, Color(0xFF29A1DE)) { onOpen(); ContactDeveloperActivity.start(ctx) }
}

@Composable
private fun InfoRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Brand.Card, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { com.alsiddiq.app.ui.components.ArtOrIcon(title, icon, tint, 46.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Icon(Icons.Outlined.ChevronLeft, null, tint = Brand.Muted)
        }
    }
}
