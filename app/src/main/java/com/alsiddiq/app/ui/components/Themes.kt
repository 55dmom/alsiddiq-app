@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alsiddiq.app.ui.theme.AppPalette
import com.alsiddiq.app.ui.theme.AppThemes
import com.alsiddiq.app.ui.theme.Brand

/** «الثيمات»: open to everyone, no password. The choice is saved on this phone and recolours the whole app and his tools. */
object ThemesUi {
    /** Debug/CI only: open the picker on the first screen for a screenshot. */
    var openOnStart = false
}

@Composable
fun ThemePickerSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Brand.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Palette, null, tint = Brand.Gold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("الثيمات", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                    Text("اختر ألوان التطبيق، وتنحفظ على جوالك", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            val occasion = AppThemes.activeOccasion
            if (occasion != null) {
                // An occasion theme is forced while its dates last; the user's own choice comes back afterwards.
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.GoldSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = Brand.Gold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ثيم «${occasion.name}» مفعّل الحين", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text("يرجع اختيارك تلقائياً بعد المناسبة", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            AppThemes.all.forEach { p ->
                ThemeOption(p, selected = AppThemes.chosenId == p.id, locked = occasion != null) { AppThemes.select(ctx, p) }
            }
        }
    }
}

@Composable
private fun ThemeOption(p: AppPalette, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !locked, shape = RoundedCornerShape(18.dp), color = p.bg,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).alpha(if (locked) 0.55f else 1f)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.gold else Brand.Line, RoundedCornerShape(18.dp))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // A small preview: header colour, card, and the three accents.
            Box(Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(p.navy)) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(18.dp).background(p.card))
                Box(Modifier.align(Alignment.Center).padding(bottom = 10.dp).width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.gold))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(p.navy, p.maroon, p.gold, p.green).forEach { c -> Box(Modifier.size(14.dp).clip(CircleShape).background(c)) }
                }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, "مختار", tint = p.gold)
        }
    }
}

/** The «الثيمات» row inside the «الأدوات» sheet (before any password). */
@Composable
fun ThemesEntry() {
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, shape = RoundedCornerShape(18.dp), color = Brand.GoldSoft, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Gold.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Palette, null, tint = Brand.Gold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("الثيمات", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                Text(currentThemeLabel(), style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronLeft, null, tint = Brand.Muted)
        }
    }
    if (open) ThemePickerSheet { open = false }
}

/** A small «الثيمات» pill for the opening screen, beside «الأدوات». */
@Composable
fun ThemesButton(tint: Color = Color.White, background: Color = Color.White.copy(alpha = 0.14f)) {
    var open by remember { mutableStateOf(ThemesUi.openOnStart) }
    Surface(onClick = { open = true }, shape = RoundedCornerShape(50), color = background) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("الثيمات", color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (open) ThemePickerSheet { open = false; ThemesUi.openOnStart = false }
}

/** "الحالي: …", or the occasion theme in force. */
fun currentThemeLabel(): String = AppThemes.activeOccasion?.let { "مناسبة: " + it.name } ?: ("الحالي: " + AppThemes.byId(AppThemes.chosenId).name)
