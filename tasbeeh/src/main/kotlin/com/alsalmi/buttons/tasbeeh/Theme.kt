package com.alsalmi.buttons.tasbeeh

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

internal object TsbColors {
    val accentLight = Color(0xFF0F8B6E)
    val accentDark = Color(0xFF3FCB9F)
}

/** This button's own theme: light / dark follow the system, Arabic RTL. */
@Composable
internal fun TsbTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = TsbColors.accentDark,
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
        primary = TsbColors.accentLight,
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
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    }
}

@Composable
internal fun TsbCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) { Box(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

internal object TsbHaptics {
    fun tap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun success(v: View) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}
