package com.alsiddiq.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * His request (Nov 8, 4:38 PM): «خلفية دينية للشاشة الرئيسية مو واضحة» — a faint Islamic geometric background.
 * Drawn as vectors (no image, no lettering, no Quran text): a tiling of 8-point stars (two squares at 45°) joined by girih
 * lines, plus an optional mosque silhouette (dome and two minarets) along the bottom. Tinted with the theme's colour.
 */

/** One 8-point star (rub el hizb shape) centred at [c] with outer radius [r]. */
private fun starPath(c: Offset, r: Float): Path = Path().apply {
    val inner = r * 0.62f
    for (i in 0 until 16) {
        val a = Math.PI / 8 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else inner
        val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.patternTiles(color: Color, cell: Float, stroke: Float) {
    val r = cell * 0.36f
    val st = Stroke(width = stroke)
    var y = 0f; var row = 0
    while (y < size.height + cell) {
        var x = if (row % 2 == 0) 0f else cell / 2
        while (x < size.width + cell) {
            val c = Offset(x, y)
            drawPath(starPath(c, r), color, style = st)
            drawCircle(color, r * 0.22f, c, style = st)
            // girih lines to the neighbouring stars
            drawLine(color, Offset(x + r, y), Offset(x + cell - r, y), stroke)
            drawLine(color, Offset(x + r * 0.7f, y + r * 0.7f), Offset(x + cell / 2 - r * 0.7f, y + cell - r * 0.7f), stroke)
            drawLine(color, Offset(x - r * 0.7f, y + r * 0.7f), Offset(x - cell / 2 + r * 0.7f, y + cell - r * 0.7f), stroke)
            x += cell
        }
        y += cell; row++
    }
}

/** A dome between two minarets, standing on the bottom edge, [h] tall. */
private fun mosquePath(w: Float, bottom: Float, h: Float): Path = Path().apply {
    val cx = w / 2
    val domeR = h * 0.34f
    val baseTop = bottom - h * 0.36f
    // the hall
    moveTo(cx - h * 0.62f, bottom); lineTo(cx - h * 0.62f, baseTop); lineTo(cx + h * 0.62f, baseTop); lineTo(cx + h * 0.62f, bottom); close()
    // the onion dome with its finial
    moveTo(cx - domeR, baseTop)
    cubicTo(cx - domeR * 1.15f, baseTop - domeR * 1.1f, cx - domeR * 0.2f, baseTop - domeR * 1.55f, cx, baseTop - domeR * 1.95f)
    cubicTo(cx + domeR * 0.2f, baseTop - domeR * 1.55f, cx + domeR * 1.15f, baseTop - domeR * 1.1f, cx + domeR, baseTop)
    close()
    addRect(androidx.compose.ui.geometry.Rect(cx - h * 0.012f, baseTop - domeR * 2.3f, cx + h * 0.012f, baseTop - domeR * 1.9f))
    // two minarets
    for (side in listOf(-1f, 1f)) {
        val mx = cx + side * h * 0.86f
        val mw = h * 0.07f
        moveTo(mx - mw, bottom); lineTo(mx - mw, bottom - h * 0.92f); lineTo(mx, bottom - h * 1.08f); lineTo(mx + mw, bottom - h * 0.92f); lineTo(mx + mw, bottom); close()
        addRect(androidx.compose.ui.geometry.Rect(mx - mw * 1.5f, bottom - h * 0.70f, mx + mw * 1.5f, bottom - h * 0.66f))
    }
}

/**
 * The faint Islamic background behind a screen's content: the star pattern at [alpha] and, when [mosque], a silhouette at the bottom.
 * Drawn behind the content, fixed to the screen (it does not scroll).
 */
fun Modifier.islamicBackdrop(tint: Color, alpha: Float = 0.06f, cell: Dp = 64.dp, mosque: Boolean = true): Modifier = drawWithCache {
    val c = tint.copy(alpha = alpha)
    val cellPx = cell.toPx()
    val stroke = 1.dp.toPx()
    val mh = (size.width * 0.42f).coerceAtMost(220.dp.toPx())
    val m = mosquePath(size.width, size.height, mh)
    onDrawBehind {
        patternTiles(c, cellPx, stroke)
        if (mosque) drawPath(m, tint.copy(alpha = alpha * 1.15f), style = Fill)
    }
}

/** The same pattern only, for the opening screen's gold overlay. */
fun Modifier.islamicPatternOverlay(color: Color, alpha: Float, cell: Dp = 72.dp): Modifier = drawWithCache {
    val c = color.copy(alpha = alpha)
    val cellPx = cell.toPx()
    val stroke = 1.dp.toPx()
    onDrawBehind { patternTiles(c, cellPx, stroke) }
}
