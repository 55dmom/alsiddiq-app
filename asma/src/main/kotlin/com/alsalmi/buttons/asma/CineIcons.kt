package com.alsalmi.buttons.asma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

internal enum class CineIcon { Back, Sound, Muted, Sliders, Images, List, ChevronRight, ChevronLeft }

/** Simple line icons drawn on a Canvas (no icon library needed). */
@Composable
internal fun CineIconView(icon: CineIcon, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val sw = w * 0.1f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg xy: Float) = Path().apply { moveTo(xy[0] * w, xy[1] * w); var i = 2; while (i < xy.size) { lineTo(xy[i] * w, xy[i + 1] * w); i += 2 } }
        when (icon) {
            CineIcon.Back, CineIcon.ChevronRight -> drawPath(p(0.38f, 0.18f, 0.7f, 0.5f, 0.38f, 0.82f), color, style = st)
            CineIcon.ChevronLeft -> drawPath(p(0.62f, 0.18f, 0.3f, 0.5f, 0.62f, 0.82f), color, style = st)
            CineIcon.Sound, CineIcon.Muted -> {
                drawPath(p(0.12f, 0.38f, 0.3f, 0.38f, 0.52f, 0.18f, 0.52f, 0.82f, 0.3f, 0.62f, 0.12f, 0.62f).apply { close() }, color)
                if (icon == CineIcon.Sound) {
                    drawArc(color, -50f, 100f, false, Offset(0.42f * w, 0.32f * w), Size(0.3f * w, 0.36f * w), style = st)
                    drawArc(color, -55f, 110f, false, Offset(0.42f * w, 0.18f * w), Size(0.48f * w, 0.64f * w), style = st)
                } else {
                    drawLine(color, Offset(0.66f * w, 0.36f * w), Offset(0.9f * w, 0.64f * w), sw, StrokeCap.Round)
                    drawLine(color, Offset(0.9f * w, 0.36f * w), Offset(0.66f * w, 0.64f * w), sw, StrokeCap.Round)
                }
            }
            CineIcon.Sliders -> {
                for ((y, x) in listOf(0.25f to 0.35f, 0.5f to 0.65f, 0.75f to 0.42f)) {
                    drawLine(color, Offset(0.1f * w, y * w), Offset(0.9f * w, y * w), sw * 0.8f, StrokeCap.Round)
                    drawCircle(color, w * 0.09f, Offset(x * w, y * w))
                }
            }
            CineIcon.Images -> {
                drawRoundRect(color, Offset(0.1f * w, 0.2f * w), Size(0.8f * w, 0.6f * w), androidx.compose.ui.geometry.CornerRadius(0.1f * w), style = st)
                drawPath(p(0.18f, 0.72f, 0.4f, 0.46f, 0.56f, 0.62f, 0.66f, 0.52f, 0.82f, 0.72f), color, style = st)
                drawCircle(color, w * 0.07f, Offset(0.68f * w, 0.36f * w))
            }
            CineIcon.List -> for (y in listOf(0.27f, 0.5f, 0.73f)) {
                drawCircle(color, w * 0.06f, Offset(0.82f * w, y * w))
                drawLine(color, Offset(0.12f * w, y * w), Offset(0.64f * w, y * w), sw, StrokeCap.Round)
            }
        }
    }
}
