package com.alsalmi.buttons.khatma

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** This button's own colours. */
internal object KhC {
    val accent: Color @Composable get() = LocalKhHost.current?.accent ?: (if (khDark()) Color(0xFFF59E0B) else Color(0xFFB45309))
    val accent2: Color @Composable get() = LocalKhHost.current?.accent2 ?: (if (khDark()) Color(0xFFFB923C) else Color(0xFFC2410C))
    val bg: Color @Composable get() = LocalKhHost.current?.bg ?: (if (khDark()) Color(0xFF0B0E12) else Color(0xFFF3F5F7))
    val card: Color @Composable get() = LocalKhHost.current?.card ?: (if (khDark()) Color(0xFF161A20) else Color.White)
    val line: Color @Composable get() = LocalKhHost.current?.line ?: (if (khDark()) Color(0xFF262C35) else Color(0xFFE4E8EE))
    val soft: Color @Composable get() = LocalKhHost.current?.soft ?: (if (khDark()) Color(0xFF42341B) else Color(0xFFF6EAE1))
    val text: Color @Composable get() = LocalKhHost.current?.text ?: (if (khDark()) Color(0xFFE8ECF1) else Color(0xFF141A21))
    val text2: Color @Composable get() = LocalKhHost.current?.text2 ?: (if (khDark()) Color(0xFF9AA4B1) else Color(0xFF5E6874))
    val paper: Color @Composable get() = if (khDark()) Color(0xFF0F1210) else Color(0xFFFBF8F1)
    val gold: Color @Composable get() = if (khDark()) Color(0xFFF2C14E) else Color(0xFFB7791F)
    val good: Color @Composable get() = if (khDark()) Color(0xFF4ADE80) else Color(0xFF15803D)
    val warn: Color @Composable get() = if (khDark()) Color(0xFFFB923C) else Color(0xFFC2410C)
    val gradient: Brush @Composable get() = LocalKhHost.current?.let { Brush.linearGradient(listOf(it.grad1, it.grad2)) } ?: (if (khDark()) Brush.linearGradient(listOf(Color(0xFF994607), Color(0xFFA4370A))) else Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFC2410C))))
}

/** Light / dark follow the system, Arabic RTL. */
@Composable
internal fun KhTheme(content: @Composable () -> Unit) {
    val host = khHostRead()
    val dark = host?.dark ?: isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFF59E0B), onPrimary = Color(0xFF06121A),
        primaryContainer = Color(0xFF42341B), onPrimaryContainer = Color(0xFFE8ECF1),
        secondary = Color(0xFFFB923C),
        background = Color(0xFF0B0E12), onBackground = Color(0xFFE8ECF1),
        surface = Color(0xFF0B0E12), onSurface = Color(0xFFE8ECF1),
        surfaceVariant = Color(0xFF161A20), onSurfaceVariant = Color(0xFF9AA4B1),
        surfaceContainer = Color(0xFF161A20), surfaceContainerHigh = Color(0xFF1C212A),
        outlineVariant = Color(0xFF262C35),
    ) else lightColorScheme(
        primary = Color(0xFFB45309), onPrimary = Color.White,
        primaryContainer = Color(0xFFF6EAE1), onPrimaryContainer = Color(0xFF141A21),
        secondary = Color(0xFFC2410C),
        background = Color(0xFFF3F5F7), onBackground = Color(0xFF141A21),
        surface = Color(0xFFF3F5F7), onSurface = Color(0xFF141A21),
        surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF5E6874),
        surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
        outlineVariant = Color(0xFFE4E8EE),
    )
    val themed = if (host == null) scheme else scheme.copy(
        primary = host.accent, onPrimary = if (host.dark) Color(0xFF0E1016) else Color.White,
        primaryContainer = host.soft, onPrimaryContainer = host.text, secondary = host.accent2,
        background = host.bg, onBackground = host.text, surface = host.bg, onSurface = host.text,
        surfaceVariant = host.card, onSurfaceVariant = host.text2, surfaceContainer = host.card, outlineVariant = host.line,
    )
    CompositionLocalProvider(LocalKhHost provides host) { MaterialTheme(colorScheme = themed) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    } }
}

/** Gentle shrink while pressed, then onClick. */
@Composable
internal fun Modifier.khPress(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    return this.scale(s).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
internal fun KhCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, border: Color = Color.Unspecified, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(KhC.card)
            .border(if (border == Color.Unspecified) 0.6.dp else 1.2.dp, if (border == Color.Unspecified) KhC.line else border, shape)
            .padding(padding),
        content = content,
    )
}

/** Gradient header card with soft circles. */
@Composable
internal fun KhHero(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(26.dp)
    val accent = KhC.accent
    Box(
        modifier.fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.35f))
            .clip(shape).background(KhC.gradient)
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = 90.dp.toPx(), center = Offset(size.width * 0.9f, -10.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = 60.dp.toPx(), center = Offset(size.width * 0.08f, size.height))
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Animated ring. */
@Composable
internal fun KhRing(progress: Float, size: Dp = 64.dp, stroke: Dp = 7.dp, onGradient: Boolean = true, center: @Composable BoxScope.() -> Unit = {}) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "ring")
    val track = if (onGradient) Color.White.copy(alpha = 0.25f) else KhC.soft
    val brush = if (onGradient) SolidColor(Color.White) else KhC.gradient
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val r = Rect(Offset(w / 2, w / 2), Size(this.size.width - w, this.size.height - w))
            drawArc(track, 0f, 360f, false, r.topLeft, r.size, style = Stroke(w))
            drawArc(brush, -90f, 360f * p, false, r.topLeft, r.size, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
internal fun KhChip(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(KhC.gradient) else Modifier.background(KhC.card).border(0.8.dp, KhC.line, shape))
            .khPress(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = if (selected) Color.White else KhC.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun KhSearch(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(KhC.card).border(0.6.dp, KhC.line, shape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = KhC.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = KhC.text2, fontSize = 16.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = KhC.text, fontSize = 16.sp), cursorBrush = SolidColor(KhC.accent), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Clear, "مسح", tint = KhC.text2, modifier = Modifier.size(20.dp).clickable { onChange("") })
    }
}

@Composable
internal fun KhEmpty(title: String, message: String, glyph: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(KhC.soft), contentAlignment = Alignment.Center) { glyph() }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = KhC.text)
        Text(message, fontSize = 14.sp, color = KhC.text2, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Simple drawn glyphs (no stars): "sunrise", "moon", "dome", "sleep", "book", "check", "clock", "pin". */
@Composable
internal fun KhGlyph(kind: String, color: Color = Color.White, size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        when (kind) {
            "sunrise" -> {
                drawArc(color, 180f, 180f, true, Offset(w * 0.15f, h * 0.30f), Size(w * 0.70f, h * 0.70f))
                drawLine(color, Offset(w * 0.02f, h * 0.67f), Offset(w * 0.98f, h * 0.67f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                drawLine(color.copy(alpha = 0.7f), Offset(w * 0.2f, h * 0.85f), Offset(w * 0.8f, h * 0.85f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
            "moon", "sleep" -> {
                val a = Path().apply { addOval(Rect(Offset(w * 0.08f, h * 0.08f), Size(w * 0.80f, h * 0.80f))) }
                val b = Path().apply { addOval(Rect(Offset(w * 0.30f, h * -0.02f), Size(w * 0.72f, h * 0.72f))) }
                val c = Path().apply { op(a, b, PathOperation.Difference) }
                drawPath(c, color)
                if (kind == "sleep") {
                    drawLine(color, Offset(w * 0.62f, h * 0.10f), Offset(w * 0.92f, h * 0.10f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.92f, h * 0.10f), Offset(w * 0.62f, h * 0.36f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.62f, h * 0.36f), Offset(w * 0.92f, h * 0.36f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                }
            }
            "sun" -> {
                drawCircle(color, radius = w * 0.26f)
                drawCircle(color.copy(alpha = 0.35f), radius = w * 0.42f, style = Stroke(w * 0.07f))
            }
            "dome" -> {
                drawArc(color, 180f, 180f, true, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f))
                drawRect(color, Offset(w * 0.12f, h * 0.52f), Size(w * 0.76f, h * 0.36f))
                drawLine(color, Offset(w * 0.5f, h * 0.02f), Offset(w * 0.5f, h * 0.2f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "book" -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.18f), Size(w * 0.40f, h * 0.66f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
                drawRoundRect(color, Offset(w * 0.52f, h * 0.18f), Size(w * 0.40f, h * 0.66f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            }
            "copy" -> {
                drawRoundRect(color, Offset(w * 0.30f, h * 0.08f), Size(w * 0.58f, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(w * 0.10f), style = Stroke(w * 0.10f))
                drawRoundRect(color, Offset(w * 0.12f, h * 0.28f), Size(w * 0.58f, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(w * 0.10f))
            }
            "ribbon" -> {
                val p = Path().apply { moveTo(w * 0.22f, h * 0.06f); lineTo(w * 0.78f, h * 0.06f); lineTo(w * 0.78f, h * 0.94f); lineTo(w * 0.5f, h * 0.72f); lineTo(w * 0.22f, h * 0.94f); close() }
                drawPath(p, color)
            }
            "chevL" -> {
                drawLine(color, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.36f, h * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.62f, h * 0.78f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            "check" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.52f), Offset(w * 0.42f, h * 0.76f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.76f), Offset(w * 0.84f, h * 0.28f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            "clock" -> {
                drawCircle(color, radius = w * 0.42f, style = Stroke(w * 0.09f))
                drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.24f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.7f, h * 0.6f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            "pin" -> {
                drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.5f, h * 0.38f))
                val p = Path().apply { moveTo(w * 0.24f, h * 0.48f); lineTo(w * 0.5f, h * 0.95f); lineTo(w * 0.76f, h * 0.48f); close() }
                drawPath(p, color)
            }
            else -> drawCircle(color, radius = w * 0.3f)
        }
    }
}

internal object KhHaptics {
    fun tap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun success(v: View) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

internal object KhText {
    /** Removes harakat / Qur'anic marks and unifies letter forms, for search. */
    fun norm(s: String): String {
        val b = StringBuilder()
        for (c in s) {
            val v = c.code
            if (v in 0x064B..0x065F || v == 0x0670 || v in 0x06D6..0x06ED || v == 0x0640) continue
            b.append(when (v) { 0x0622, 0x0623, 0x0625, 0x0671 -> 'ا'; 0x0629 -> 'ه'; 0x0649, 0x0626 -> 'ي'; 0x0624 -> 'و'; in 0x0660..0x0669 -> '0' + (v - 0x0660); else -> c })
        }
        return b.toString()
    }
}


// ===== «الثيمات» من تطبيق «مركز الصديق»: طبقة الألوان فقط، النصوص والمنطق ما تغيّرت.
// التطبيق يكتب ألوان الثيم المختار (أو ثيم المناسبة) في «alsiddiq_theme». الثيم الأصلي أو بدون اختيار = ألوان هذا الزر الأصلية كما هي.
internal class KhHostColors(val dark: Boolean, val accent: Color, val accent2: Color, val bg: Color, val card: Color, val line: Color, val soft: Color, val text: Color, val text2: Color, val grad1: Color, val grad2: Color)
internal val LocalKhHost = androidx.compose.runtime.staticCompositionLocalOf<KhHostColors?> { null }
/** Reads the colours the app wrote for its tools (only when a theme other than the original is in force). */
internal fun khHostFrom(sp: android.content.SharedPreferences): KhHostColors? {
    if (!sp.getBoolean("tools_on", false)) return null
    fun c(k: String) = Color(sp.getInt(k, 0))
    return KhHostColors(sp.getBoolean("t_dark", false), c("t_accent"), c("t_accent2"), c("t_bg"), c("t_card"), c("t_line"), c("t_soft"), c("t_text"), c("t_text2"), c("t_grad1"), c("t_grad2"))
}
@Composable
internal fun khHostRead(): KhHostColors? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember { khHostFrom(ctx.getSharedPreferences("alsiddiq_theme", android.content.Context.MODE_PRIVATE)) }
}
@Composable
internal fun khDark(): Boolean = LocalKhHost.current?.dark ?: isSystemInDarkTheme()
