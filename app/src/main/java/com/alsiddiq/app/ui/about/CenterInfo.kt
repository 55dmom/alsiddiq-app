package com.alsiddiq.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alsalmi.buttons.contact.ContactDeveloperActivity
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.theme.Brand

/**
 * «عن المركز» and «تواصل مع المطوّر» rows — in «الأدوات» (before any password) and in the settings of every section.
 * «تواصل مع المطوّر» is his own fixed module (contact): not stored anywhere, not editable by anyone.
 */
@Composable
fun CenterInfoEntries(onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    val about = LocalContainer.current.center.about.collectAsState().value
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("عن المركز", "تعريف بالمركز وعنوانه وأرقام التواصل", Icons.Outlined.Info, Brand.Navy) { onOpen(); AboutActivity.start(ctx) }
        DeveloperRow(onOpen)
        if (about.contactButton) ContactCentreRow(about.contactPhone, onOpen)
    }
}

/** His «تواصل مع المطوّر» button (opens his ContactDeveloperActivity as given). */
@Composable
fun DeveloperRow(onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    InfoRow("تواصل مع المطوّر", "اقتراح، سؤال، أو استفسار", Icons.AutoMirrored.Outlined.Send, Color(0xFF29A1DE)) { onOpen(); ContactDeveloperActivity.start(ctx) }
}

/** v3.9: «تواصل مع المركز» — shown only when the admin switched it on (default off). Taps dial the admin's number; with no number it opens «عن المركز». */
@Composable
fun ContactCentreRow(phone: String, onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    InfoRow("تواصل مع المركز", "اتصال بالمركز", Icons.Outlined.Call, Brand.Green) {
        onOpen()
        val num = dialable(phone)
        if (num.isNotEmpty()) ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$num")))
        else AboutActivity.start(ctx)
    }
}

/** Digits and a leading + only: what the dialer takes. */
fun dialable(phone: String): String = phone.trim().filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '+') }

/** The admin's switch «تواصل مع المركز» (default off) and the number it dials. Stored with «عن المركز» so everyone reads it before a password. */
@Composable
fun ContactButtonSwitch() {
    val repo = LocalContainer.current.center
    val about = repo.about.collectAsState().value
    val on = about.contactButton
    val actions = rememberActions()
    var number by remember(about.contactPhone) { mutableStateOf(about.contactPhone) }
    Surface(shape = RoundedCornerShape(18.dp), color = Brand.Card, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable { actions.run(null, { repo.saveContactButton(!on, number) }) { } }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تواصل مع المركز", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                    Text("زر يظهر للجميع في الصفحة الرئيسية، ويتصل بالرقم اللي تكتبه — اختياري", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                Switch(checked = on, onCheckedChange = { v -> actions.run(null, { repo.saveContactButton(v, number) }) { } })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = number, onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(20) },
                label = { Text("رقم التواصل") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
            TextButton(onClick = { actions.run(null, { repo.saveContactButton(on, number) }) { } }, enabled = number != about.contactPhone) { Text("حفظ الرقم") }
        }
    }
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
