package com.alsiddiq.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alsiddiq.app.ui.theme.AppThemes
import com.alsiddiq.app.ui.theme.Brand
import com.alsiddiq.app.ui.theme.OccasionPalettes
import com.alsiddiq.app.ui.theme.Occasions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** His icon names (Apple symbol names in his file) → the nearest Material icon. */
fun occasionIcon(name: String): ImageVector = when (name.removeSuffix(".fill")) {
    "moon.stars" -> Icons.Outlined.NightsStay
    "gift" -> Icons.Outlined.CardGiftcard
    "sun.max" -> Icons.Outlined.WbSunny
    "sunrise" -> Icons.Outlined.Brightness5
    "calendar" -> Icons.Outlined.Event
    "moon", "moon.circle" -> Icons.Outlined.DarkMode
    "flag" -> Icons.Outlined.Flag
    else -> Icons.Outlined.Star
}

/** The small greeting card at the top of home and of the password screen, only while an occasion is active. */
@Composable
fun OccasionBanner(modifier: Modifier = Modifier) {
    val o = AppThemes.activeOccasion ?: return
    val card = o.card ?: return
    val tint = OccasionPalettes.cardColor(card.color, Brand.palette.dark)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Card)
        .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(occasionIcon(card.icon), null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
            Text(card.body, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        }
    }
}

/** «بطاقة معايدة»: a centred popup over the screen when the app opens, once a day per occasion. */
@Composable
fun OccasionPopupHost() {
    val ctx = LocalContext.current
    val o = AppThemes.activeOccasion ?: return
    val card = o.card ?: return
    val day = Occasions.simulate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val key = "popup_" + o.id
    val prefs = remember { ctx.getSharedPreferences(AppThemes.PREFS, android.content.Context.MODE_PRIVATE) }
    var show by remember(o.id) { mutableStateOf(prefs.getString(key, null) != day) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1700); ready = true } // after the splash
    if (!show || !ready) return
    val close = { prefs.edit().putString(key, day).apply(); show = false }
    val tint = OccasionPalettes.cardColor(card.color, Brand.palette.dark)
    val scale = remember { Animatable(0.82f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { fade.animateTo(1f, tween(380)) }
            scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().scale(scale.value).alpha(fade.value)
                .clip(RoundedCornerShape(28.dp)).background(Brand.Card)) {
                Box(Modifier.fillMaxWidth().height(132.dp).background(Brush.linearGradient(listOf(Brand.Navy, Brand.NavyMid, Brand.NavyDark))),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(58.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                            Icon(occasionIcon(card.icon), null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brand.Gold))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.title, style = MaterialTheme.typography.headlineSmall, color = Brand.Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(card.body, style = MaterialTheme.typography.bodyLarge, color = Brand.Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = close, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Navy, contentColor = Color.White)) {
                        Text("شكراً", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
