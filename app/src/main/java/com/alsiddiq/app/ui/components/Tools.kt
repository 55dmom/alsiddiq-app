@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alsalmi.buttons.adhkar.AdhkarActivity
import com.alsalmi.buttons.asma.AsmaAllahActivity
import com.alsalmi.buttons.duas.DuasActivity
import com.alsalmi.buttons.hifz.HifzActivity
import com.alsalmi.buttons.khatma.KhatmaActivity
import com.alsalmi.buttons.mushaf.MushafActivity
import com.alsalmi.buttons.prayer.PrayerTimesActivity
import com.alsalmi.buttons.tasbeeh.TasbeehActivity
import com.alsiddiq.app.ui.theme.Brand

/**
 * «الأدوات»: السالمي's own side buttons, open to everyone (no password). Each one is an independent module.
 * To add the next one (المصحف، الأذكار…): add its module and ONE line to [Tools.all].
 */
class ToolItem(val title: String, val sub: String, val icon: ImageVector, private val colorOf: () -> Color, val open: (Context) -> Unit) {
    /** Read at drawing time, so it follows the chosen theme. */
    val color: Color get() = colorOf()
}

object Tools {
    /** Shown on its own at the top of the «الأدوات» tab. */
    val prayer = ToolItem("مواقيت الصلاة", "الأوقات والإقامة والتنبيه، بدون إنترنت", Icons.Outlined.Mosque, { Brand.Orange }) { PrayerTimesActivity.start(it) }
    val all: List<ToolItem> = listOf(
        ToolItem("المصحف", "المصحف كامل، صفحة صفحة", Icons.Outlined.MenuBook, { Brand.Green }) { MushafActivity.start(it) },
        ToolItem("وضع الحفظ", "احفظ أي سورة أو مقطع وراجع محفوظك", Icons.Outlined.Bookmark, { Brand.Navy }) { HifzActivity.start(it) },
        ToolItem("الختمة", "خطة ختم القرآن ومتابعة وردك", Icons.Outlined.DateRange, { Brand.Maroon }) { KhatmaActivity.start(it) },
        prayer,
        ToolItem("الأذكار", "أذكار الصباح والمساء وغيرها", Icons.Outlined.WbSunny, { Brand.Navy }) { AdhkarActivity.start(it) },
        ToolItem("الأدعية", "أدعية من القرآن والسنة", Icons.Outlined.VolunteerActivism, { Brand.Green }) { DuasActivity.start(it) },
        ToolItem("المسبحة", "عداد تسبيح وأذكار", Icons.Outlined.TouchApp, { Brand.Gold }) { TasbeehActivity.start(it) },
        ToolItem("أسماء الله الحسنى", "الأسماء الـ99 ومعانيها", Icons.Outlined.AutoAwesome, { Brand.Maroon }) { AsmaAllahActivity.start(it) },
    )
    /** Debug/CI only: open the menu on the first screen for a screenshot. */
    var openOnStart = false
}

@Composable
fun ToolsSheet(onDismiss: () -> Unit) {
    // 7 tools now: open fully and scroll on small phones so none is cut off.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Brand.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ToolsMenuContent(onDismiss, Modifier.padding(bottom = 28.dp))
    }
}

/**
 * His layout (Nov 8): «مواقيت الصلاة» open on its own (his screen, embedded), then the other tools as buttons, in their order.
 * One shared block: the «الأدوات» tab and the opening screen (under the password card). No password; the host page scrolls it.
 */
@Composable
fun ToolsBlock(titleColor: Color = Brand.Navy) {
    val ctx = LocalContext.current
    val others = Tools.all.filter { it !== Tools.prayer }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.alsalmi.buttons.prayer.PrayerTimesPanel()
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GridView, null, tint = Brand.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("الأدوات", style = MaterialTheme.typography.titleMedium, color = titleColor)
        }
        others.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Surface(onClick = { t.open(ctx) }, shape = RoundedCornerShape(18.dp), color = Brand.Card, shadowElevation = 1.dp, modifier = Modifier.weight(1f).heightIn(min = 104.dp)) {
                        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(t.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(t.icon, null, tint = t.color)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(t.title, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The «الأدوات» tab in the bottom bar: the shared block only. */
@Composable
fun ToolsTabScreen() {
    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolsBlock()   // his request (Nov 8, 9:42 PM): themes / about / developer live in the settings only
        Spacer(Modifier.height(8.dp))
    }
}

/** The menu itself (the sheet on the opening screen). */
@Composable
private fun ToolsMenuContent(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).then(modifier)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.GridView, null, tint = Brand.Gold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("الأدوات", style = MaterialTheme.typography.titleLarge, color = Brand.Navy)
                    Text("للجميع وبدون كلمة سر", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Tools.all.forEach { t ->
                Surface(onClick = { onDismiss(); t.open(ctx) }, shape = RoundedCornerShape(18.dp), color = Brand.Bg,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(t.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = t.color)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            Text(t.sub, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        }
                        Icon(Icons.Outlined.ChevronLeft, null, tint = Brand.Muted)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("أدوات أكثر قريباً", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
}

/** The «الأدوات» button (opening screen header, etc.). */
@Composable
fun ToolsButton(tint: Color = Color.White, background: Color = Color.White.copy(alpha = 0.14f)) {
    var open by remember { mutableStateOf(Tools.openOnStart) }
    Surface(onClick = { open = true }, shape = RoundedCornerShape(50), color = background) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GridView, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("الأدوات", color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (open) ToolsSheet { open = false; Tools.openOnStart = false }
}

/** A row of tool cards (viewer home). */
@Composable
fun ToolsRow(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Tools.all.chunked(2).forEach { pair ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pair.forEach { t ->
            Surface(onClick = { t.open(ctx) }, shape = RoundedCornerShape(18.dp), color = Brand.Card, shadowElevation = 1.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(t.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(t.icon, null, tint = t.color)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, textAlign = TextAlign.Center)
                }
            }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
    } }
}
