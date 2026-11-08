package com.alsiddiq.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsiddiq.app.R

/** One colour set for the whole app. «الثيمات» in Settings picks one of [AppThemes.all]; the choice is saved on the phone. */
data class AppPalette(
    val id: String, val name: String, val dark: Boolean,
    val navy: Color, val navyMid: Color, val navyDark: Color, val navySoft: Color,
    val maroon: Color, val maroonSoft: Color, val gold: Color, val goldSoft: Color,
    val green: Color, val greenSoft: Color, val orange: Color, val red: Color,
    val bg: Color, val card: Color, val ink: Color, val muted: Color, val line: Color,
    /** Colours handed to his tool screens (their own colour layer reads them). null = each tool keeps its original look. */
    val tools: ToolColors? = null,
)

/** What his tools receive: accent colours, page/card colours, text colours and their header gradient. */
data class ToolColors(
    val dark: Boolean, val accent: Color, val accent2: Color, val bg: Color, val card: Color, val line: Color,
    val soft: Color, val text: Color, val text2: Color, val grad1: Color, val grad2: Color,
)

object AppThemes {
    /** The centre's own colours, taken from its logo (the original look). */
    val Default = AppPalette("default", "الأصلي: كحلي وعنابي وذهبي", false,
        navy = Color(0xFF282564), navyMid = Color(0xFF3A3486), navyDark = Color(0xFF1B1947), navySoft = Color(0xFFE9E8F5),
        maroon = Color(0xFF6B2A2C), maroonSoft = Color(0xFFF6E9E9), gold = Color(0xFFC9A24A), goldSoft = Color(0xFFFBF3E0),
        green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
        bg = Color(0xFFF7F7FB), card = Color(0xFFFFFFFF), ink = Color(0xFF1E1D2F), muted = Color(0xFF6E6D80), line = Color(0xFFE6E5EF))
    val Emerald = AppPalette("emerald", "زمردي وذهبي", false,
        navy = Color(0xFF0E5A44), navyMid = Color(0xFF14735A), navyDark = Color(0xFF083D2F), navySoft = Color(0xFFE2F1EB),
        maroon = Color(0xFF8A6418), maroonSoft = Color(0xFFF6EEDC), gold = Color(0xFFC9A24A), goldSoft = Color(0xFFFBF3E0),
        green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
        bg = Color(0xFFF4F8F6), card = Color(0xFFFFFFFF), ink = Color(0xFF15241E), muted = Color(0xFF627069), line = Color(0xFFDDE8E3),
        tools = ToolColors(false, Color(0xFF0E5A44), Color(0xFFB08A2E), Color(0xFFF4F8F6), Color(0xFFFFFFFF), Color(0xFFDDE8E3), Color(0xFFE2F1EB), Color(0xFF15241E), Color(0xFF627069), Color(0xFF0E5A44), Color(0xFF14735A)))
    val Night = AppPalette("night", "الليل الداكن", true,
        navy = Color(0xFF636BD6), navyMid = Color(0xFF454BA8), navyDark = Color(0xFF151838), navySoft = Color(0xFF23264A),
        maroon = Color(0xFFC9666C), maroonSoft = Color(0xFF3A2327), gold = Color(0xFFD9B45E), goldSoft = Color(0xFF392F1B),
        green = Color(0xFF4CB98A), greenSoft = Color(0xFF1B3A2D), orange = Color(0xFFE59A4A), red = Color(0xFFE5675A),
        bg = Color(0xFF0E1016), card = Color(0xFF181B25), ink = Color(0xFFE9EAF2), muted = Color(0xFFA0A2B4), line = Color(0xFF2A2E3D),
        tools = ToolColors(true, Color(0xFF8B93F0), Color(0xFFD9B45E), Color(0xFF0E1016), Color(0xFF181B25), Color(0xFF2A2E3D), Color(0xFF23264A), Color(0xFFE9EAF2), Color(0xFFA0A2B4), Color(0xFF3A3F94), Color(0xFF1F2350)))
    val Sand = AppPalette("sand", "كريمي ورملي", false,
        navy = Color(0xFF5E4630), navyMid = Color(0xFF765A3E), navyDark = Color(0xFF3F2E1F), navySoft = Color(0xFFF1E8DA),
        maroon = Color(0xFF94502F), maroonSoft = Color(0xFFF6E7DE), gold = Color(0xFFB98A3E), goldSoft = Color(0xFFF8EEDB),
        green = Color(0xFF4F7A45), greenSoft = Color(0xFFE9F1E3), orange = Color(0xFFCC7A2E), red = Color(0xFFB8432F),
        bg = Color(0xFFFAF5EC), card = Color(0xFFFFFDF8), ink = Color(0xFF2E261D), muted = Color(0xFF7B6F61), line = Color(0xFFEADFCD),
        tools = ToolColors(false, Color(0xFF7A5A3A), Color(0xFF94502F), Color(0xFFFAF5EC), Color(0xFFFFFDF8), Color(0xFFEADFCD), Color(0xFFF1E8DA), Color(0xFF2E261D), Color(0xFF7B6F61), Color(0xFF5E4630), Color(0xFF94502F)))
    val Royal = AppPalette("royal", "أزرق ملكي", false,
        navy = Color(0xFF1E3A8A), navyMid = Color(0xFF2747A8), navyDark = Color(0xFF142766), navySoft = Color(0xFFE5EBFA),
        maroon = Color(0xFF7C2D45), maroonSoft = Color(0xFFF6E6EC), gold = Color(0xFFD1A23A), goldSoft = Color(0xFFFBF2DC),
        green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
        bg = Color(0xFFF5F7FC), card = Color(0xFFFFFFFF), ink = Color(0xFF17203A), muted = Color(0xFF66708A), line = Color(0xFFE0E5F1),
        tools = ToolColors(false, Color(0xFF1E3A8A), Color(0xFF2747A8), Color(0xFFF5F7FC), Color(0xFFFFFFFF), Color(0xFFE0E5F1), Color(0xFFE5EBFA), Color(0xFF17203A), Color(0xFF66708A), Color(0xFF1E3A8A), Color(0xFF2747A8)))
    val all = listOf(Default, Emerald, Night, Sand, Royal)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Default

    /** Saved on the phone only. His tool modules read the colours written here through their own colour layer. */
    const val PREFS = "alsiddiq_theme"

    /** The user's own choice (what the picker shows as selected). */
    var chosenId by mutableStateOf("default")
        private set
    /** An occasion theme in force right now (forced, overrides the user's choice), or null. */
    var activeOccasion: Occasion? by mutableStateOf(null)
        private set

    fun load(ctx: Context) {
        chosenId = byId(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", null)).id
        apply(ctx)
    }
    fun select(ctx: Context, p: AppPalette) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("theme", p.id).apply()
        chosenId = p.id
        apply(ctx)
    }
    /** Occasion first (forced while its dates last), else the user's choice; then hand the colours to his tools. */
    private fun apply(ctx: Context) {
        activeOccasion = Occasions.activeNow()
        val p = activeOccasion?.palette ?: byId(chosenId)
        Brand.palette = p
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val t = p.tools
        if (t == null) e.putBoolean("tools_on", false) else {
            e.putBoolean("tools_on", true).putBoolean("t_dark", t.dark)
            listOf("t_accent" to t.accent, "t_accent2" to t.accent2, "t_bg" to t.bg, "t_card" to t.card, "t_line" to t.line,
                "t_soft" to t.soft, "t_text" to t.text, "t_text2" to t.text2, "t_grad1" to t.grad1, "t_grad2" to t.grad2)
                .forEach { (k, c) -> e.putInt(k, c.toArgb()) }
        }
        e.apply()
    }
}

/** The app's colours, read from the chosen theme (changing the theme recolours every screen at once). */
object Brand {
    var palette: AppPalette by mutableStateOf(AppThemes.Default)
    val Navy get() = palette.navy
    val NavyMid get() = palette.navyMid
    val NavyDark get() = palette.navyDark
    val NavySoft get() = palette.navySoft
    val Maroon get() = palette.maroon
    val MaroonSoft get() = palette.maroonSoft
    val Gold get() = palette.gold
    val GoldSoft get() = palette.goldSoft
    val Green get() = palette.green
    val GreenSoft get() = palette.greenSoft
    val Orange get() = palette.orange
    val Red get() = palette.red
    val Bg get() = palette.bg
    val Card get() = palette.card
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Line get() = palette.line
}

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold)
)

private fun t(size: Int, w: FontWeight, line: Int = (size * 1.45).toInt()) =
    TextStyle(fontFamily = Plex, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)

private val AppTypography = Typography(
    displaySmall = t(32, FontWeight.Bold),
    headlineMedium = t(26, FontWeight.Bold),
    headlineSmall = t(22, FontWeight.Bold),
    titleLarge = t(20, FontWeight.SemiBold),
    titleMedium = t(16, FontWeight.SemiBold),
    titleSmall = t(14, FontWeight.SemiBold),
    bodyLarge = t(16, FontWeight.Normal),
    bodyMedium = t(14, FontWeight.Normal),
    bodySmall = t(12, FontWeight.Normal),
    labelLarge = t(14, FontWeight.Medium),
    labelMedium = t(12, FontWeight.Medium),
    labelSmall = t(11, FontWeight.Medium)
)

private fun schemeOf(p: AppPalette): ColorScheme = if (!p.dark) lightColorScheme(
    primary = p.navy,
    onPrimary = Color.White,
    primaryContainer = p.navySoft,
    onPrimaryContainer = p.navyDark,
    secondary = p.maroon,
    onSecondary = Color.White,
    secondaryContainer = p.maroonSoft,
    onSecondaryContainer = p.maroon,
    tertiary = p.gold,
    background = p.bg,
    onBackground = p.ink,
    surface = p.card,
    onSurface = p.ink,
    surfaceVariant = lerp(p.bg, p.line, 0.35f),
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.card,
    surfaceContainerLow = lerp(p.card, p.bg, 0.5f),
    surfaceContainer = lerp(p.bg, p.line, 0.15f),
    surfaceContainerHigh = lerp(p.bg, p.line, 0.3f),
    surfaceContainerHighest = lerp(p.bg, p.line, 0.5f),
    outline = lerp(p.line, p.muted, 0.35f),
    outlineVariant = p.line,
    error = p.red
) else darkColorScheme(
    primary = p.navy,
    onPrimary = Color.White,
    primaryContainer = p.navySoft,
    onPrimaryContainer = p.ink,
    secondary = p.maroon,
    onSecondary = Color.White,
    secondaryContainer = p.maroonSoft,
    onSecondaryContainer = p.ink,
    tertiary = p.gold,
    background = p.bg,
    onBackground = p.ink,
    surface = p.card,
    onSurface = p.ink,
    surfaceVariant = lerp(p.card, p.line, 0.5f),
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.bg,
    surfaceContainerLow = lerp(p.bg, p.card, 0.6f),
    surfaceContainer = p.card,
    surfaceContainerHigh = lerp(p.card, p.line, 0.35f),
    surfaceContainerHighest = lerp(p.card, p.line, 0.6f),
    outline = lerp(p.line, p.muted, 0.4f),
    outlineVariant = p.line,
    error = p.red,
    onError = Color.White
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AlSiddiqTheme(content: @Composable () -> Unit) {
    // Status-bar icons: light on the dark theme, otherwise what the system would pick.
    val view = androidx.compose.ui.platform.LocalView.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = Brand.palette.dark
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !(dark || systemDark)
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = schemeOf(Brand.palette), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
