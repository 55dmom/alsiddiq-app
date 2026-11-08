package com.alsalmi.buttons.asma

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal object AsmColors {
    val accentLight = Color(0xFF7A5A12)
    val accentDark = Color(0xFFE0B64F)
}

/** This button's own theme: light / dark follow the system, Arabic RTL. */
@Composable
internal fun AsmTheme(content: @Composable () -> Unit) {
    val host = asmHostRead()
    val dark = host?.dark ?: isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = AsmColors.accentDark,
        onPrimary = Color(0xFF00201A),
        primaryContainer = Color(0xFF16302A),
        onPrimaryContainer = Color(0xFFCDEDE3),
        background = Color(0xFF0E1110),
        onBackground = Color(0xFFE6EBE9),
        surface = Color(0xFF0E1110),
        onSurface = Color(0xFFE6EBE9),
        surfaceVariant = Color(0xFF1A1F1D),
        onSurfaceVariant = Color(0xFFB5BFBB),
        surfaceContainer = Color(0xFF1A1F1D),
        outlineVariant = Color(0xFF2A312E),
    ) else lightColorScheme(
        primary = AsmColors.accentLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8F3EF),
        onPrimaryContainer = Color(0xFF0B3B30),
        background = Color(0xFFF4F6F5),
        onBackground = Color(0xFF151A18),
        surface = Color(0xFFF4F6F5),
        onSurface = Color(0xFF151A18),
        surfaceVariant = Color.White,
        onSurfaceVariant = Color(0xFF5A6461),
        surfaceContainer = Color.White,
        outlineVariant = Color(0xFFE3E8E6),
    )
    val themed = if (host == null) scheme else scheme.copy(
        primary = host.accent, onPrimary = if (host.dark) Color(0xFF0E1016) else Color.White,
        primaryContainer = host.soft, onPrimaryContainer = host.text, secondary = host.accent2,
        background = host.bg, onBackground = host.text, surface = host.bg, onSurface = host.text,
        surfaceVariant = host.card, onSurfaceVariant = host.text2, surfaceContainer = host.card, outlineVariant = host.line,
    )
    CompositionLocalProvider(LocalAsmHost provides host) { MaterialTheme(colorScheme = themed) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    } }
}

@Composable
internal fun AsmCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) { Box(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

internal object AsmHaptics {
    fun tap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun success(v: View) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}


// ===== «الثيمات» من تطبيق «مركز الصديق»: طبقة الألوان فقط، النصوص والمنطق ما تغيّرت.
// التطبيق يكتب ألوان الثيم المختار (أو ثيم المناسبة) في «alsiddiq_theme». الثيم الأصلي أو بدون اختيار = ألوان هذا الزر الأصلية كما هي.
internal class AsmHostColors(val dark: Boolean, val accent: Color, val accent2: Color, val bg: Color, val card: Color, val line: Color, val soft: Color, val text: Color, val text2: Color, val grad1: Color, val grad2: Color)
internal val LocalAsmHost = androidx.compose.runtime.staticCompositionLocalOf<AsmHostColors?> { null }
/** Reads the colours the app wrote for its tools (only when a theme other than the original is in force). */
internal fun asmHostFrom(sp: android.content.SharedPreferences): AsmHostColors? {
    if (!sp.getBoolean("tools_on", false)) return null
    fun c(k: String) = Color(sp.getInt(k, 0))
    return AsmHostColors(sp.getBoolean("t_dark", false), c("t_accent"), c("t_accent2"), c("t_bg"), c("t_card"), c("t_line"), c("t_soft"), c("t_text"), c("t_text2"), c("t_grad1"), c("t_grad2"))
}
@Composable
internal fun asmHostRead(): AsmHostColors? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember { asmHostFrom(ctx.getSharedPreferences("alsiddiq_theme", android.content.Context.MODE_PRIVATE)) }
}
@Composable
internal fun asmDark(): Boolean = LocalAsmHost.current?.dark ?: isSystemInDarkTheme()
