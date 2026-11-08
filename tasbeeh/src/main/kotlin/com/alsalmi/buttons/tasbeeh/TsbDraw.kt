package com.alsalmi.buttons.tasbeeh

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

// Ported from the تفاؤل cinematic المسبحة: the wave breathes, beads glide one spacing per tap (0.32 s ease-out from wherever they are,
// so fast taps stay continuous), ripple ring, "+1", light particles.

/** Bead flow state, in seconds on the frame clock. */
internal class TsbFlow {
    var from = -9.0; var to = 0.0; var t0 = 0.0; var dur = 1.25
    val taps = ArrayList<Double>()
    var done = -100.0
    fun phase(t: Double): Double {
        val k = ((t - t0) / dur).coerceIn(0.0, 1.0)
        val e = if (dur > 0.5) 1 - (1 - k).pow(3) else 1 - (1 - k).pow(2)
        return from + (to - from) * e
    }
    fun tap(t: Double) {
        from = phase(t); to = Math.round(to).toDouble() + 1; t0 = t; dur = 0.32
        taps.add(t); while (taps.size > 6) taps.removeAt(0)
    }
    fun enter(t: Double) { from = to - 9; t0 = t; dur = 1.25 }
    fun lastTap() = taps.lastOrNull() ?: -100.0
}

internal object TsbDraw {
    fun rnd(i: Int, salt: Int): Double {
        var x = (i.toLong() * 2654435761L + salt * 40503L) xor -0x61c8864680b583ebL
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L; x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L; x = x xor (x ushr 31)
        return ((x % 10000 + 10000) % 10000) / 10000.0
    }
    fun waveY(x: Float, w: Float, cy: Float, t: Double): Float {
        val a0 = w * 0.052
        val breathe = 1 + 0.10 * sin(2 * PI * t / 5.6)
        val q = ((x - w / 2) / w).toDouble()
        val main = cos(2 * PI * q) * a0 * breathe
        val drift = sin(2 * PI * (q * 1.4) - 2 * PI * t / 7.5) * a0 * 0.16
        return cy + (main + drift).toFloat()
    }
    private fun smooth(x: Float) = x * x * (3 - 2 * x)

    fun particles(d: DrawScope, t: Double) = with(d) {
        val w = size.width; val h = size.height
        for (i in 0 until 28) {
            val s1 = rnd(i, 1); val s2 = rnd(i, 2); val s3 = rnd(i, 3)
            val speed = (5 + 9 * s2) * density
            val span = h * 0.62
            val m = ((s1 * span + t * speed) % span)
            val yy = h - (h * 0.04f + m.toFloat())
            val xx = w * s3.toFloat() + (sin(t * (0.3 + s1 * 0.4) + i) * 14 * density).toFloat()
            val life = m / span
            val fade = sin(PI * life)
            val tw = 0.55 + 0.45 * sin(t * (1.2 + s2 * 1.6) + i * 1.7)
            val r = ((0.8 + 1.5 * s2) * density).toFloat()
            val a = (0.55 * fade * tw).toFloat()
            val p = Offset(xx, yy)
            drawCircle(Brush.radialGradient(listOf(Color(0.85f, 0.95f, 1f, a * 0.35f), Color.Transparent), p, r * 3), r * 3, p)
            drawCircle(Color(0.92f, 0.97f, 1f, a), r, p)
        }
    }

    fun string(d: DrawScope, t: Double, flow: TsbFlow, markerBase: Double, target: Int, cy: Float, plus: (Offset, Float, Float) -> Unit) = with(d) {
        val w = size.width
        val n = 180
        val x0 = -w * 0.06f; val x1 = w * 1.06f
        val px = FloatArray(n + 1); val py = FloatArray(n + 1); val len = FloatArray(n + 1)
        for (i in 0..n) {
            val x = x0 + (x1 - x0) * i / n
            px[i] = x; py[i] = waveY(x, w, cy, t)
            if (i > 0) len[i] = len[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
        }
        fun at(s: Float): Offset {
            if (s <= 0f) return Offset(px[0], py[0]); if (s >= len[n]) return Offset(px[n], py[n])
            var lo = 0; var hi = n
            while (hi - lo > 1) { val m = (lo + hi) / 2; if (len[m] < s) lo = m else hi = m }
            val k = (s - len[lo]) / max(len[hi] - len[lo], 0.0001f)
            return Offset(px[lo] + (px[hi] - px[lo]) * k, py[lo] + (py[hi] - py[lo]) * k)
        }
        var sc: Float
        run { var lo = 0; var hi = n; while (hi - lo > 1) { val m = (lo + hi) / 2; if (px[m] < w / 2) lo = m else hi = m }; sc = len[lo] }
        val path = Path().apply { moveTo(px[0], py[0]); for (i in 1..n) lineTo(px[i], py[i]) }
        drawPath(path, Brush.horizontalGradient(0f to Color.Transparent, 0.16f to Color.White.copy(alpha = 0.42f), 0.5f to Color.White.copy(alpha = 0.55f),
            0.84f to Color.White.copy(alpha = 0.42f), 1f to Color.Transparent, startX = 0f, endX = w), style = Stroke(1.1f * density))

        val ph = flow.phase(t)
        val fl = floor(ph); val fr = ph - fl
        val gap = w / 22.5f
        val rb = w / 54f
        val since = t - flow.lastTap()
        val tapGlow = if (since < 0.6) (1 - since / 0.6).pow(2) else 0.0
        val centre = at(sc)
        val gr = rb * (5.2f + 1.6f * tapGlow.toFloat())
        drawOval(Brush.radialGradient(listOf(Color(0.80f, 0.96f, 1f, (0.26 + 0.30 * tapGlow).toFloat()), Color.Transparent), centre, gr),
            Offset(centre.x - gr, centre.y - gr * 0.9f), Size(gr * 2, gr * 1.8f))
        val doneK = t - flow.done
        if (doneK < 1.4) {
            val k = (doneK / 1.4).toFloat(); val rr = rb * (3 + 22 * k)
            drawCircle(Brush.radialGradient(listOf(Color(1f, 0.85f, 0.6f, 0.35f * (1 - k)), Color.Transparent), centre, rr), rr, centre)
        }
        val step = if (target == 100) 10 else 11
        val order = (-14..14).sortedByDescending { abs(it + fr) }
        for (k in order) {
            val u = k + fr
            val p = at(sc + (u * gap).toFloat())
            val ex = (p.x / (w * 0.16f)).coerceIn(0f, 1f); val ex2 = ((w - p.x) / (w * 0.16f)).coerceIn(0f, 1f)
            val edge = smooth(ex) * smooth(ex2)
            if (edge <= 0.01f) continue
            val dd = abs(u)
            val emph = 1 + 0.32 * exp(-(dd * dd) / 2.2)
            val j = fl.toLong() - k
            val rel = j - markerBase.roundToLong()
            val kind = if (((rel % target) + target) % target == 0L) 2 else if (((rel % step) + step) % step == 0L) 1 else 0
            val r = rb * emph.toFloat() * (if (kind == 2) 1.18f else 1f) * (0.88f + 0.12f * edge)
            bead(this, p, r, kind, edge, if (dd < 0.5) tapGlow.toFloat() else 0f)
        }
        val cr = rb * 1.32f
        drawCircle(Color.White.copy(alpha = 0.20f), cr * 1.75f, centre, style = Stroke(0.8f * density))
        for (tt in flow.taps) {
            val k = ((t - tt) / 0.5).toFloat(); if (k < 0f || k >= 1f) continue
            val e = 1 - (1 - k).pow(2)
            drawCircle(Color(0.86f, 0.98f, 1f, 0.75f * (1 - k) * (1 - k)), cr * (1.75f + 2.6f * e), centre, style = Stroke((1.6f * (1 - k) + 0.4f) * density))
        }
        for (tt in flow.taps) {
            val k = ((t - tt) / 0.75).toFloat(); if (k < 0f || k >= 1f) continue
            val pop = if (k < 0.18f) 0.7f + 0.3f * (k / 0.18f) * 1.15f else 1f
            plus(Offset(centre.x + cr * 2.3f, centre.y - cr * 2.6f - 26 * density * (1 - (1 - k).pow(2))), (1 - k) * (1 - k * 0.2f), pop)
        }
    }

    fun bead(d: DrawScope, p: Offset, r: Float, kind: Int, alpha: Float, lit: Float) = with(d) {
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f * alpha), Color.Transparent), Offset(p.x, p.y + r * 0.45f), r * 1.2f),
            Offset(p.x - r * 1.1f, p.y - r * 0.5f), Size(r * 2.2f, r * 2f))
        val cols = when (kind) {
            1 -> listOf(Color(1f, 0.86f, 0.80f), Color(0.95f, 0.42f, 0.28f), Color(0.62f, 0.13f, 0.08f), Color(0.25f, 0.04f, 0.03f))
            2 -> listOf(Color(1f, 0.95f, 0.82f), Color(1f, 0.74f, 0.38f), Color(0.78f, 0.42f, 0.12f), Color(0.32f, 0.15f, 0.04f))
            else -> listOf(Color(0.97f, 1f, 1f), Color(0.76f, 0.91f, 0.93f), Color(0.40f, 0.58f, 0.64f), Color(0.13f, 0.22f, 0.27f))
        }.map { it.copy(alpha = alpha) }
        val hl = Offset(p.x - r * 0.36f, p.y - r * 0.40f)
        drawCircle(Brush.radialGradient(0f to cols[0], 0.28f to cols[1], 0.72f to cols[2], 1f to cols[3], center = hl, radius = r * 1.75f), r, p)
        val bl = Offset(p.x + r * 0.30f, p.y + r * 0.42f)
        val bc = if (kind == 0) Color(0.75f, 1f, 1f) else Color(1f, 0.85f, 0.6f)
        drawCircle(Brush.radialGradient(listOf(bc.copy(alpha = 0.42f * alpha), Color.Transparent), bl, r * 0.75f), r, p)
        if (lit > 0f) drawCircle(Color.White.copy(alpha = 0.35f * lit * alpha), r, p)
        drawCircle(Color.White.copy(alpha = 0.28f * alpha), r, p, style = Stroke(max(0.5f * density, r * 0.07f)))
        drawOval(Color.White.copy(alpha = 0.92f * alpha), Offset(hl.x - r * 0.20f, hl.y - r * 0.13f), Size(r * 0.40f, r * 0.26f))
    }
}