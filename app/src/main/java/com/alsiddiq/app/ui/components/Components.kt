package com.alsiddiq.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsiddiq.app.domain.AttendanceStatus
import com.alsiddiq.app.domain.Grade
import com.alsiddiq.app.domain.RecordKind
import com.alsiddiq.app.ui.theme.Brand
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .shadow(6.dp, shape, ambientColor = Brand.Navy.copy(alpha = 0.08f), spotColor = Brand.Navy.copy(alpha = 0.10f))
            .clip(shape)
            .background(Brand.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Brand.Maroon) }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { ArtOrIcon(label, icon, tint, 38.dp, 22.dp) }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Brand.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, color: Color = Brand.Navy) {
    val initials = name.split(" ").filter { it.isNotBlank() && it != "الشيخ" && it != "الأستاذ" }
        .take(2).joinToString("") { it.take(1) }
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = if (filled) Color.White else color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

fun gradeColor(g: Grade): Color = when (g) {
    Grade.EXCELLENT -> Brand.Green
    Grade.VERY_GOOD -> Color(0xFF3C8D7A)
    Grade.GOOD -> Brand.Navy
    Grade.PASS -> Brand.Orange
    Grade.WEAK -> Brand.Red
}

fun kindColor(k: RecordKind): Color = when (k) {
    RecordKind.HIFZ -> Brand.Navy
    RecordKind.TASMEE -> Brand.Gold
    RecordKind.MURAJA -> Brand.Maroon
}

fun attendanceColor(s: AttendanceStatus): Color = when (s) {
    AttendanceStatus.PRESENT -> Brand.Green
    AttendanceStatus.LATE -> Brand.Orange
    AttendanceStatus.EXCUSED -> Brand.Navy
    AttendanceStatus.ABSENT -> Brand.Red
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Brand.NavySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inbox, null, tint = Brand.Navy, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft)
            .border(1.dp, Brand.Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "وضع تجريبي: البيانات محفوظة على هذا الجهاز فقط ولا تتم مزامنتها بعد",
            style = MaterialTheme.typography.labelMedium, color = if (Brand.palette.dark) Brand.Gold else Color(0xFF7A5C14)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Bg, titleContentColor = Brand.Ink)
    )
}

/** Animated circular progress with the value in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    color: Color = Brand.Navy,
    track: Color = Brand.NavySoft,
    center: @Composable () -> Unit = {}
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val anim by animateFloatAsState(if (started) progress.coerceIn(0f, 1f) else 0f, tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            if (anim > 0f) drawArc(color, -90f, 360f * anim, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

data class BarDatum(val label: String, val value: Float, val detail: String)

/** Interactive bar chart: tap a bar to see its value. Draws right-to-left (newest on the left in RTL). */
@Composable
fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = Brand.Navy,
    highlight: Color = Brand.Maroon
) {
    if (data.isEmpty()) return
    var selected by remember(data) { mutableIntStateOf(data.lastIndex) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(800, easing = FastOutSlowInEasing), label = "bars")
    val max = (data.maxOf { it.value }).coerceAtLeast(1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier) {
        val sel = data.getOrNull(selected)
        if (sel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(sel.label, highlight)
                Spacer(Modifier.width(8.dp))
                Text(sel.detail, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
            }
            Spacer(Modifier.height(10.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(data) {
                detectTapGestures { pos ->
                    val slot = size.width / data.size.toFloat()
                    var i = (pos.x / slot).toInt().coerceIn(0, data.lastIndex)
                    if (rtl) i = data.lastIndex - i
                    selected = i
                }
            }
        ) {
            val slot = size.width / data.size
            val barW = slot * 0.56f
            for (i in data.indices) {
                val pos = if (rtl) data.lastIndex - i else i
                val h = (data[i].value / max) * (size.height - 8f) * grow
                val x = pos * slot + (slot - barW) / 2
                drawRoundRect(
                    color = Brand.Line,
                    topLeft = Offset(x, 0f), size = Size(barW, size.height),
                    cornerRadius = CornerRadius(barW / 2, barW / 2), alpha = 0.45f
                )
                drawRoundRect(
                    color = if (i == selected) highlight else color,
                    topLeft = Offset(x, size.height - h), size = Size(barW, h.coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(barW / 2, barW / 2)
                )
            }
        }
    }
}

data class Slice(val label: String, val value: Float, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, size: Dp = 130.dp, centerText: String = "") {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweep by animateFloatAsState(if (started) 1f else 0f, tween(900), label = "donut")
    var selected by remember(slices) { mutableIntStateOf(-1) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 22.dp.toPx()
                val d = this.size.minDimension - s
                val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
                if (total <= 0f) {
                    drawArc(Brand.Line, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
                } else {
                    var start = -90f
                    slices.forEachIndexed { i, sl ->
                        val ang = 360f * (sl.value / total) * sweep
                        drawArc(sl.color, start, (ang - 1.5f).coerceAtLeast(0f), false, tl, Size(d, d),
                            style = Stroke(if (i == selected) s * 1.25f else s))
                        start += ang
                    }
                }
            }
            val label = if (selected in slices.indices && total > 0)
                "${(slices[selected].value / total * 100).roundToInt()}%" else centerText
            Text(label, style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            slices.forEachIndexed { i, sl ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { selected = if (selected == i) -1 else i }.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(sl.color))
                    Spacer(Modifier.width(8.dp))
                    Text(sl.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(sl.value.roundToInt().toString(), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                }
            }
        }
    }
}

@Composable
fun LinearMeter(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = Brand.Red) else ButtonDefaults.buttonColors()
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun shortDayLabel(d: LocalDate): String = when (d.dayOfWeek.value) {
    6 -> "سبت"; 7 -> "أحد"; 1 -> "إثنين"; 2 -> "ثلاثاء"; 3 -> "أربعاء"; 4 -> "خميس"; else -> "جمعة"
}

/** Light (white) status-bar icons over the navy headers, dark icons over light screens. */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !lightIcons
    }
}
