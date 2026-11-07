package com.alsiddiq.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsiddiq.app.R

// Palette taken from the centre's logo.
object Brand {
    val Navy = Color(0xFF282564)
    val NavyDark = Color(0xFF1B1947)
    val NavySoft = Color(0xFFE9E8F5)
    val Maroon = Color(0xFF6B2A2C)
    val MaroonSoft = Color(0xFFF6E9E9)
    val Gold = Color(0xFFC9A24A)
    val GoldSoft = Color(0xFFFBF3E0)
    val Green = Color(0xFF2E7D5B)
    val GreenSoft = Color(0xFFE5F3EC)
    val Orange = Color(0xFFD9822B)
    val Red = Color(0xFFC0392B)
    val Bg = Color(0xFFF7F7FB)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E1D2F)
    val Muted = Color(0xFF6E6D80)
    val Line = Color(0xFFE6E5EF)
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

private val Scheme = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Brand.NavySoft,
    onPrimaryContainer = Brand.NavyDark,
    secondary = Brand.Maroon,
    onSecondary = Color.White,
    secondaryContainer = Brand.MaroonSoft,
    onSecondaryContainer = Brand.Maroon,
    tertiary = Brand.Gold,
    background = Brand.Bg,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF1F1F6),
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color(0xFFF4F4F9),
    surfaceContainerHigh = Color(0xFFEFEFF6),
    surfaceContainerHighest = Color(0xFFE9E9F2),
    outline = Color(0xFFCFCEDC),
    outlineVariant = Brand.Line,
    error = Brand.Red
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
