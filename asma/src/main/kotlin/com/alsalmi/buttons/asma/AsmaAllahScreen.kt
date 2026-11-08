package com.alsalmi.buttons.asma

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Needle motion: 0.46 s ease-out with a small settle (back, c1 = 1.25). */
internal class AsmaFlow {
    var from = 0.0; var to = 0.0; var t0 = -10.0; var dur = 0.46
    var pulseAt = -10.0; var doneAt = -10.0
    fun pos(t: Double): Double { val p = ((t - t0) / dur).coerceIn(0.0, 1.0); return from + (to - from) * ease(p) }
    fun go(target: Double, t: Double, d: Double = 0.46) { from = pos(t); to = target; t0 = t; dur = d }
    fun jump(target: Double) { from = target; to = target; t0 = -10.0 }
    companion object { fun ease(p: Double): Double { val c1 = 1.25; val c3 = c1 + 1; val q = p - 1; return 1 + c3 * q * q * q + c1 * q * q } }
}

internal object AsmaUi {
    val red = Color(0xFFE5233D)
    val redHi = Color(0xFFFF5A4E)
    val tajawalMedium = FontFamily(Font(R.font.asma_tajawal_medium, FontWeight.Medium))
    const val COUNT = 99
}

/**
 * «أسماء الله الحسنى» — cinematic version: a translucent dark dial with 99 ticks and the names around it over a moving scene
 * (bundled, offline). Each tap glides the red needle to the next name with a glass tick; the end of the 99 plays a soft cue.
 * [onBack] is optional: when given, a back button shows at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmaAllahScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Cine.prefs(ctx) }
    var scene by remember { mutableStateOf(Cine.nextOnOpen(ctx)) }
    var muted by remember { mutableStateOf(false) }
    var showScenes by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var idx by remember { mutableIntStateOf(prefs.getInt("idx", 0).coerceIn(0, AsmaUi.COUNT - 1)) }
    var done by remember { mutableStateOf(prefs.getBoolean("done", false)) }
    var fwd by remember { mutableStateOf(true) }
    val clock = rememberCineClock()
    val flow = remember { AsmaFlow().apply { jump(idx.toDouble()) } }
    LaunchedEffect(Unit) {
        CineAudio.init(ctx)
        muted = CineAudio.muted
        CineAudio.ambient(ctx, scene.sound)
        withFrameNanos { t -> flow.from = idx - 6.0; flow.to = idx.toDouble(); flow.t0 = t / 1e9; flow.dur = 1.1 }
    }
    DisposableEffect(Unit) { onDispose { CineAudio.leave() } }
    fun save() { prefs.edit().putInt("idx", idx).putBoolean("done", done).apply() }
    fun tick() { CineAudio.tap(); view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    val next: () -> Unit = {
        val t = clock.value
        fwd = true
        if (done) { done = false; idx = 0; flow.go(0.0, t, 1.25); tick() }
        else if (idx >= AsmaUi.COUNT - 1) { done = true; flow.doneAt = t; flow.pulseAt = t; CineAudio.complete(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        else { idx += 1; flow.go(idx.toDouble(), t); flow.pulseAt = t; tick() }
        save()
    }
    val prev: () -> Unit = {
        val t = clock.value
        fwd = false
        if (done) { done = false; tick() }
        else if (idx > 0) { idx -= 1; flow.go(idx.toDouble(), t); tick() }
        save()
    }
    val jump: (Int) -> Unit = { i -> fwd = i >= idx; done = false; idx = i.coerceIn(0, AsmaUi.COUNT - 1); flow.go(idx.toDouble(), clock.value, 0.9); flow.pulseAt = clock.value; tick(); save() }
    CineTest.tap = next

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (CineTest.noBackground) Color.Transparent else Color(0xFF05040C))) {
            if (!CineTest.noBackground) Crossfade(scene, animationSpec = tween(900), label = "asma-bg") { s -> CineBackground(s) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.16f to Color.Black.copy(alpha = 0.30f), 0.45f to Color.Black.copy(alpha = 0.22f),
                0.62f to Color.Black.copy(alpha = 0.42f), 1f to Color.Black.copy(alpha = 0.74f))))
            val dens = LocalDensity.current
            val W = maxWidth; val H = maxHeight
            val ti = CineTest.insetsDp
            val statusTop: Dp = ti?.let { it.first.dp } ?: with(dens) { WindowInsets.statusBars.getTop(dens).toDp() }
            val navBottom: Dp = ti?.let { it.second.dp } ?: with(dens) { WindowInsets.navigationBars.getBottom(dens).toDp() }
            val headerH = 98.dp
            val nameH = 200.dp
            val avail = (H - statusTop - headerH - nameH - navBottom).value - 70f
            val R: Dp = min(W.value * 0.46f, avail / 2f).coerceAtLeast(110f).dp
            val dialTop = statusTop + headerH + ((avail - 2 * R.value) * 0.45f).coerceIn(4f, 50f).dp
            val dialCy = dialTop + R + 4.dp

            AsmaDial(flow, clock, idx, done, R, dialCy, Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { CineTest.tap?.invoke() } }
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                        // right-to-left: swipe right = next
                        if (abs(dx) > 48.dp.toPx()) { if (dx > 0) next() else prev() }
                    }) { _, d -> dx += d }
                })
            // header: back · title · scenes · sound
            Column(Modifier.fillMaxWidth().padding(top = statusTop)) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp)) { if (onBack != null) CineGlassButton(CineIcon.Back) { onBack() } }
                    Text("أسماء الله الحسنى", Modifier.weight(1f).padding(horizontal = 6.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 1, fontFamily = Cine.tajawal)
                    CineGlassButton(CineIcon.Images) { showScenes = true }
                    Spacer(Modifier.width(8.dp))
                    CineGlassButton(if (muted) CineIcon.Muted else CineIcon.Sound) { muted = !muted; CineAudio.muted = muted }
                }
                AsmaProgress(flow, clock, idx, done, Modifier.padding(horizontal = 26.dp, vertical = 8.dp))
            }
            // name + meaning
            Box(Modifier.fillMaxWidth().padding(top = dialCy + R + 18.dp).height(nameH).pointerInput(Unit) { detectTapGestures { CineTest.tap?.invoke() } }) {
                AnimatedContent(idx to done, transitionSpec = {
                    val up = if (fwd) 1 else -1
                    (fadeIn(tween(320, delayMillis = 90)) + slideInVertically(tween(380, delayMillis = 60)) { it / 9 * up }) togetherWith
                        (fadeOut(tween(170)) + slideOutVertically(tween(220)) { -it / 14 * up })
                }, label = "asma-name") { (i, d) ->
                    if (d) AsmaDoneBlock { CineTest.tap?.invoke() } else AsmaNameBlock(ASMA_NAMES[i])
                }
            }
            // prev · all names · next
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = navBottom + 14.dp).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (idx == 0 && !done) Text("اضغط للانتقال للاسم التالي", Modifier.padding(bottom = 8.dp), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, maxLines = 1, fontFamily = AsmaUi.tajawalMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CineGlassButton(CineIcon.ChevronRight, enabled = idx > 0 || done) { prev() }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { showList = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CineIconView(CineIcon.List, Color.White, 15.dp)
                            Text("كل الأسماء", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, fontFamily = Cine.tajawal)
                        }
                    }
                    CineGlassButton(CineIcon.ChevronLeft) { next() }
                }
            }
        }
        if (showScenes) {
            ModalBottomSheet(onDismissRequest = { showScenes = false }, containerColor = Color(0xFF121216), contentColor = Color.White) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CineScenePicker(scene.id) { s -> scene = s; Cine.save(ctx, s); CineAudio.ambient(ctx, s.sound); showScenes = false }
                }
            }
        }
        if (showList) {
            ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = Color(0xFF121216), contentColor = Color.White) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(ASMA_NAMES) { i, n ->
                            val on = i == idx && !done
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (on) 0.14f else 0.05f))
                                .border(1.2.dp, if (on) AsmaUi.red.copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { jump(i); showList = false }.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${n.n}", Modifier.width(30.dp), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.width(10.dp))
                                Text(n.name, color = Color.White, fontSize = 22.sp, fontFamily = Cine.amiri)
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

/** «18 / 99» and a 10-segment bar that fills with the needle. */
@Composable
private fun AsmaProgress(flow: AsmaFlow, clock: State<Double>, idx: Int, done: Boolean, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("\u200E${if (done) AsmaUi.COUNT else idx + 1} / ${AsmaUi.COUNT}", color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 58.dp))
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val segs = 10; val gap = 2.5.dp.toPx(); val sw = (size.width - gap * (segs - 1)) / segs
            val f = if (done) 1.0 else ((flow.pos(clock.value) + 1.0) / AsmaUi.COUNT).coerceIn(0.0, 1.0)
            val filled = f * segs
            val cr = CornerRadius(size.height / 2)
            for (s in 0 until segs) {
                val x = s * (sw + gap)
                val xs = if (layoutDirection == LayoutDirection.Rtl) size.width - x - sw else x
                drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(xs, 0f), androidx.compose.ui.geometry.Size(sw, size.height), cr)
                val part = (filled - s).coerceIn(0.0, 1.0).toFloat()
                if (part > 0f) {
                    val w = sw * part
                    val x0 = if (layoutDirection == LayoutDirection.Rtl) xs + sw - w else xs
                    drawRoundRect(Brush.horizontalGradient(listOf(AsmaUi.red, AsmaUi.redHi), startX = x0, endX = x0 + sw), Offset(x0, 0f), androidx.compose.ui.geometry.Size(w, size.height), cr)
                }
            }
        }
    }
}

@Composable
private fun AsmaNameBlock(n: AsmName) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.name, color = Color.White.copy(alpha = 0.96f), fontSize = 46.sp, fontFamily = Cine.amiri, textAlign = TextAlign.Center, maxLines = 1,
            style = TextStyle(lineHeight = 74.sp, shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 3f), 18f)))
        Box(Modifier.padding(top = 2.dp).width(30.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(AsmaUi.red))
        Spacer(Modifier.height(12.dp))
        Text(n.meaning, color = Color.White.copy(alpha = 0.78f), fontSize = 16.5.sp, fontFamily = AsmaUi.tajawalMedium, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, lineHeight = 26.sp, maxLines = 3, style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 10f)))
    }
}

@Composable
private fun AsmaDoneBlock(restart: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("أتممت الأسماء الحسنى التسعة والتسعين", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, fontFamily = Cine.tajawal)
        Spacer(Modifier.height(8.dp))
        Text("«" + ASMA_HADITH + "»", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontFamily = Cine.amiri, textAlign = TextAlign.Center, lineHeight = 27.sp, maxLines = 3)
        Text(ASMA_HADITH_REF, color = Color.White.copy(alpha = 0.48f), fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 1, fontFamily = AsmaUi.tajawalMedium)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(AsmaUi.red).clickable { restart() }.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("ابدأ من جديد", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Cine.tajawal)
        }
    }
}

@Composable
private fun AsmaDial(flow: AsmaFlow, clock: State<Double>, idx: Int, done: Boolean, Rdp: Dp, cyDp: Dp, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 120)
    val dens = LocalDensity.current
    val R = with(dens) { Rdp.toPx() }
    val labelPx = R * 0.050f
    val labels: List<TextLayoutResult> = remember(R) {
        val fs = with(dens) { labelPx.toSp() }
        ASMA_NAMES.map { measurer.measure(it.plain, TextStyle(color = Color.White, fontSize = fs, fontFamily = Cine.tajawal, fontWeight = FontWeight.Bold)) }
    }
    val hubText = rememberTextMeasurer()
    Canvas(modifier) {
        val t = clock.value
        CineParticles.draw(this, t, 22)
        val a = flow.pos(t)
        val cx = size.width / 2f
        val cy = cyDp.toPx()
        val c = Offset(cx, cy)
        val N = AsmaUi.COUNT
        val step = 360.0 / N
        fun dir(k: Double): Offset { val th = Math.toRadians(k * step); return Offset(sin(th).toFloat(), -cos(th).toFloat()) }
        // soft glow behind + drop shadow ring
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), c, R * 1.35f), R * 1.35f, c)
        drawCircle(Brush.radialGradient(0.80f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, center = c, radius = R * 1.07f), R * 1.07f, c)
        // disk + bevel rim
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1E1E22).copy(alpha = 0.80f), Color(0xFF16161A).copy(alpha = 0.84f), Color(0xFF111114).copy(alpha = 0.90f)), c, R), R, c)
        drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f)), Offset(cx - R, cy - R), Offset(cx + R, cy + R)), R - 1.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.35f), R * 0.975f, c, style = Stroke(1.dp.toPx()))
        // thin inner guide rings
        drawCircle(Color.White.copy(alpha = 0.06f), R * 0.685f, c, style = Stroke(0.8.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.045f), R * 0.865f, c, style = Stroke(0.8.dp.toPx()))
        // ticks
        val lit = if (done) N.toDouble() else a + 0.5
        for (k in 0 until N) {
            val d = dir(k.toDouble())
            val major = k % 11 == 0
            val r2 = R * 0.952f; val r1 = if (major) R * 0.872f else R * 0.905f
            val on = k < lit
            val near = (1.0 - abs(k - a)).coerceIn(0.0, 1.0).toFloat()
            val col = if (on) Color.White.copy(alpha = if (major) 0.95f else 0.78f) else Color.White.copy(alpha = if (major) 0.42f else 0.22f)
            drawLine(col, c + d * r1, c + d * r2, strokeWidth = (if (major) 1.7f else 1.05f).dp.toPx() + near * 0.6f, cap = StrokeCap.Round)
        }
        // names around (radial; right half reads outward, left half reads inward — always upright-ish), outer ends aligned
        val rOut = R * 0.845f
        for (k in 0 until N) {
            val lay = labels[k]
            val deg = (k * step).toFloat()
            val hl = if (done) 0f else (1.0 - abs(k - a)).coerceIn(0.0, 1.0).toFloat()
            val passed = done || k <= idx
            val base = if (passed) 0.80f else 0.40f
            val alpha = base + hl * (1f - base)
            val maxW = R * 0.33f
            val w0 = lay.size.width.toFloat(); val h0 = lay.size.height.toFloat()
            val fit = if (w0 > maxW) maxW / w0 else 1f
            val sc = fit * (1f + 0.30f * hl)
            val right = deg < 180f
            rotate(if (right) deg - 90f else deg + 90f, c) {
                val w = w0 * sc
                val x0 = if (right) cx + rOut - w else cx - rOut
                scale(sc, sc, Offset(x0, cy)) {
                    if (hl > 0.02f) drawRoundRect(Color.Black.copy(alpha = 0.55f * hl), Offset(x0 - 3.dp.toPx() / sc, cy - h0 / 2f - 1.dp.toPx() / sc),
                        androidx.compose.ui.geometry.Size(w0 + 6.dp.toPx() / sc, h0 + 2.dp.toPx() / sc), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx() / sc))
                    drawText(lay, color = Color.White, topLeft = Offset(x0, cy - h0 / 2f), alpha = alpha)
                }
            }
        }
        // centre hub
        val hubR = R * 0.245f
        val pulse = ((t - flow.pulseAt) / 0.7).toFloat()
        if (pulse in 0f..1f) drawCircle(AsmaUi.red.copy(alpha = 0.35f * (1f - pulse)), hubR * (1f + 0.42f * pulse), c, style = Stroke((2.4f * (1f - pulse) + 0.6f).dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF17171A), Color(0xFF0C0C0E)), c, hubR), hubR, c)
        drawCircle(Color.White.copy(alpha = 0.13f), hubR, c, style = Stroke(1.dp.toPx()))
        run {
            val hs = if (pulse in 0f..1f) 1f + 0.06f * sin(Math.PI.toFloat() * pulse) else 1f
            val lay = hubText.measure("${if (done) AsmaUi.COUNT else idx + 1}", TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = with(this) { (hubR * 0.62f * hs).toSp() }, fontFamily = Cine.tajawal, fontWeight = FontWeight.Bold))
            drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y - lay.size.height / 2f + hubR * 0.04f))
        }
        // completion: a red light runs once around the rim
        val dp = ((t - flow.doneAt) / 1.6).toFloat()
        if (done && dp in 0f..1f) {
            val d = dir(dp * N.toDouble())
            drawCircle(Brush.radialGradient(listOf(AsmaUi.redHi.copy(alpha = 0.8f * (1f - dp)), Color.Transparent), c + d * (R * 0.93f), R * 0.12f), R * 0.12f, c + d * (R * 0.93f))
        }
        // needle
        val d = dir(a)
        val p0 = c + d * (hubR + 4.dp.toPx())
        val p1 = c + d * (R * 0.60f)
        drawLine(AsmaUi.red.copy(alpha = 0.22f), p0, p1, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(AsmaUi.red, p0, p1, strokeWidth = 2.3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color(0xFF0E0E10), 5.dp.toPx(), p0)
        drawCircle(AsmaUi.red, 5.dp.toPx(), p0, style = Stroke(2.dp.toPx()))
        // marker ring on the tick band
        val pm = c + d * (R * 0.912f)
        drawCircle(AsmaUi.red.copy(alpha = 0.25f), 7.5.dp.toPx(), pm)
        drawCircle(AsmaUi.redHi, 5.5.dp.toPx(), pm, style = Stroke(1.8.dp.toPx()))
    }
}

/** Ready-made full-screen Activity: `AsmaAllahActivity.start(context)`. */
class AsmaAllahActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AsmaAllahScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AsmaAllahActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
