package com.alsalmi.buttons.tasbeeh

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * «المسبحة» — cinematic version: a full-screen moving scene (bundled, offline), a thin breathing string of glass beads that glides
 * one bead per tap, glass ticks + a soft cue at the end of each round, and the scene's ambient sound.
 * [onBack] is optional: when given, a back button shows at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbeehScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val st = remember { TasbeehState(ctx) }
    var scene by remember { mutableStateOf(Cine.nextOnOpen(ctx)) }
    var muted by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showScenes by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        CineAudio.init(ctx)
        muted = CineAudio.muted
        CineAudio.ambient(ctx, scene.sound)
    }
    DisposableEffect(Unit) { onDispose { CineAudio.leave() } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val clock = rememberCineClock()
        val flow = remember { TsbFlow() }
        var markerBase by remember { mutableDoubleStateOf(-st.count.toDouble()) }
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { withFrameNanos { flow.enter(it / 1e9) }; enter.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        val measurer = rememberTextMeasurer()
        val sel = st.selected.coerceIn(0, TasbeehState.phrases.size - 1)
        val onTap = {
            val reached = st.tap()
            flow.tap(clock.value)
            if (reached) {
                flow.done = clock.value; markerBase = flow.to
                CineAudio.complete()
                if (st.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } else {
                CineAudio.tap()
                if (st.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        val select = { i: Int -> if (st.selected != i) { st.select(i); markerBase = flow.to - st.count; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
        CineTest.tap = onTap
        BoxWithConstraints(Modifier.fillMaxSize().then(if (CineTest.noBackground) Modifier else Modifier.background(Color(0xFF030408)))) {
            val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
            val dens = LocalDensity.current
            val cyR = 0.47f
            val ti = CineTest.insetsDp
            val statusTop = ti?.let { with(dens) { it.first.dp.toPx() } } ?: WindowInsets.statusBars.getTop(dens).toFloat()
            val navBottom = ti?.let { with(dens) { it.second.dp.toPx() } } ?: WindowInsets.navigationBars.getBottom(dens).toFloat()
            var textHpx by remember { mutableIntStateOf(0) }
            val regTop = statusTop + with(dens) { 56.dp.toPx() }
            val bandTop = hPx * cyR - wPx * 0.075f
            val region = max(bandTop - regTop, with(dens) { 80.dp.toPx() })
            val th = if (textHpx > 0) textHpx.toFloat() else with(dens) { 180.dp.toPx() }
            val tsc = if (th > region) max(region / th, 0.72f) else 1f
            val ty = regTop + max(region - th * tsc, 0f) * 0.3f
            if (!CineTest.noBackground) {
                Crossfade(scene, animationSpec = tween(900), label = "tsb-bg") { s -> CineBackground(s) }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.50f), 0.30f to Color.Black.copy(alpha = 0.18f), 0.46f to Color.Transparent,
                0.70f to Color.Black.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.58f))))
            // tap anywhere to count
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onTap() })
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }) {
                val t = clock.value
                TsbDraw.particles(this, t)
                TsbDraw.string(this, t, flow, markerBase, st.target.coerceAtLeast(1), size.height * cyR) { at, a, pop ->
                    val lay = measurer.measure("\u200E+1", TextStyle(color = Color(1f, 0.80f, 0.62f), fontSize = (15 * pop).sp, fontWeight = FontWeight.Bold))
                    drawText(lay, topLeft = Offset(at.x - lay.size.width / 2f, at.y - lay.size.height / 2f), alpha = a.coerceIn(0f, 1f))
                }
            }
            // top: back · scenes · mute · options
            Row(Modifier.fillMaxWidth().then(if (ti != null) Modifier.padding(top = ti.first.dp) else Modifier.statusBarsPadding()).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) CineGlassButton(CineIcon.Back) { onBack() }
                Spacer(Modifier.weight(1f))
                CineGlassButton(CineIcon.Images) { showScenes = true }
                Spacer(Modifier.width(8.dp))
                CineGlassButton(if (muted) CineIcon.Muted else CineIcon.Sound) { muted = !muted; CineAudio.muted = muted }
                Spacer(Modifier.width(8.dp))
                CineGlassButton(CineIcon.Sliders) { showOptions = true }
            }
            // dhikr block
            Column(Modifier.fillMaxWidth().offset { IntOffset(0, ty.roundToInt()) }.padding(horizontal = 22.dp)
                .onSizeChanged { if (it.height != textHpx) textHpx = it.height }
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * -14.dp.toPx(); scaleX = tsc; scaleY = tsc; transformOrigin = TransformOrigin(0.5f, 0f) }
                .pointerInput(sel) {
                    var dx = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                        // right-to-left: a swipe to the right shows the next dhikr
                        if (abs(dx) > 44.dp.toPx()) { val n = TasbeehState.phrases.size; select((sel + (if (dx > 0) 1 else n - 1)) % n) }
                    }) { _, d -> dx += d }
                }
                .pointerInput(Unit) { detectTapGestures { onTap() } },
                horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(sel, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) }, label = "tsb-dhikr") { s ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val ph = TasbeehState.phrases[s]
                        Text(ph.text, Modifier.fillMaxWidth(), color = Color.White, fontSize = if (ph.text.length > 80) 26.sp else if (ph.text.length > 60) 28.sp else if (ph.text.length > 30) 34.sp else 42.sp,
                            lineHeight = if (ph.text.length > 30) 52.sp else 64.sp, fontFamily = Cine.amiri, textAlign = TextAlign.Center, maxLines = 3,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 3f), 18f)))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(5.dp).background(Cine.warm, CircleShape))
                        Spacer(Modifier.height(10.dp))
                        Text(ph.ref, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 10f)))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in TasbeehState.phrases.indices) {
                        Box(Modifier.size(14.dp, 20.dp).clickable(remember { MutableInteractionSource() }, null) { select(i) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(if (i == sel) 6.dp else 5.dp).background(Color.White.copy(alpha = if (i == sel) 0.85f else 0.25f), CircleShape))
                        }
                    }
                }
            }
            // counter under the string
            val cyDp = with(dens) { (hPx * cyR + wPx * 0.052f + 80.dp.toPx()).toDp() }
            Column(Modifier.align(Alignment.TopCenter).offset(y = cyDp - 43.dp).graphicsLayer { alpha = enter.value; val s = 0.9f + 0.1f * enter.value; scaleX = s; scaleY = s }
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { showOptions = true }) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                val t = clock.value
                val k = (t - flow.lastTap()) / 0.24
                val pop = if (k in 0.0..1.0) 1 + 0.10 * sin(PI * k) else 1.0
                val dk = t - flow.done
                Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                    val p = (st.count.toFloat() / st.target.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
                        if (dk in 0.0..0.9) {
                            val q = (dk / 0.9).toFloat()
                            drawCircle(Color(1f, 0.86f, 0.62f, 0.6f * (1 - q)), r * (1 + 0.55f * q), c, style = Stroke(1.5f * density))
                        }
                        drawCircle(Color.Black.copy(alpha = 0.46f), r, c)
                        val sw = 2.5f * density; val inset = 3 * density + sw / 2
                        drawCircle(Color.White.copy(alpha = 0.13f), r - inset, c, style = Stroke(sw))
                        drawArc(Color(0.80f, 0.95f, 0.98f), -90f, 360f * p, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Text(Cine.num(st.count), Modifier.offset(y = 1.dp).scale(pop.toFloat()), color = Color.White, fontSize = if (st.count >= 10000) 22.sp else 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Text("\u200E/" + Cine.num(st.target), Modifier.width(120.dp), color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            // totals pill (opens the options)
            val counterBottom = hPx * cyR + wPx * 0.052f + with(dens) { (80 + 43 + 8 + 18).dp.toPx() }
            val statsY = min(max(hPx - navBottom - with(dens) { 70.dp.toPx() }, counterBottom + with(dens) { 30.dp.toPx() }), hPx - navBottom - with(dens) { 30.dp.toPx() })
            var statsH by remember { mutableIntStateOf(0) }
            Row(Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (statsY - statsH / 2f).roundToInt()) }.onSizeChanged { statsH = it.height }
                .graphicsLayer { alpha = enter.value }
                .clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.26f))
                .clickable { showOptions = true }.padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = Color.White.copy(alpha = 0.84f)
                val sh = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 8f))
                for ((i, w) in listOf("الدورات", Cine.num(st.rounds), "|", "المجموع", Cine.num(st.total)).withIndex())
                    if (w == "|") Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(12.dp).background(c.copy(alpha = 0.45f)))
                    else Text(w, color = c, fontSize = 14.5.sp, fontWeight = if (i == 1 || i == 4) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, style = sh)
            }
        }
        if (showOptions) {
            ModalBottomSheet(onDismissRequest = { showOptions = false }, containerColor = Color(0xFF121216), contentColor = Color.White) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TsbOptions(st, select = { select(it); showOptions = false }, onTarget = { markerBase = flow.to - st.count },
                        onReset = { markerBase = flow.to; showOptions = false })
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
    }
}

/** Options: dhikr list, target (33 / 99 / 100 / custom), vibration, totals, reset. */
@Composable
private fun TsbOptions(st: TasbeehState, select: (Int) -> Unit, onTarget: () -> Unit, onReset: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val warm = Color(0xFFFAA85C)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("المسبحة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        for (i in TasbeehState.phrases.indices) {
            val on = st.selected == i
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (on) 0.14f else 0.06f))
                .clickable { select(i) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (on) warm else Color.White.copy(alpha = 0.18f), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(TasbeehState.phrases[i].text, color = Color.White, fontSize = 19.sp, fontFamily = Cine.amiri, lineHeight = 30.sp)
                    Text(TasbeehState.phrases[i].ref, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Text(Cine.num(st.counts[i]), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in TasbeehState.presetTargets) Chip(Cine.num(v), st.target == v, warm, Modifier.weight(1f)) { st.chooseTarget(v); onTarget() }
            Chip(if (st.isCustomTarget) Cine.num(st.target) else "مخصص", st.isCustomTarget, warm, Modifier.weight(1f)) { custom = true }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).clickable { st.toggleHaptics() }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (st.haptics) "الاهتزاز: شغّال" else "الاهتزاز: مقفّل", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((title, n) in listOf("العدد" to st.count, "الدورات" to st.rounds, "المجموع" to st.total))
                Column(Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(Cine.num(n), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.65f))
                }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).clickable { st.resetCurrent(); onReset() }, contentAlignment = Alignment.Center) {
                Text("تصفير", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.Red.copy(alpha = 0.14f)).clickable { confirmAll = true }, contentAlignment = Alignment.Center) {
                Text("تصفير كل العدّادات", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF6B6B), maxLines = 1)
            }
        }
    }
    if (custom) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { custom = false },
            title = { Text("عدد مخصص") },
            text = { OutlinedTextField(txt, { v -> txt = v.filter { it.isDigit() }.take(6) }, placeholder = { Text("مثال: 1000") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = {
                val latin = txt.map { ch -> Character.getNumericValue(ch).takeIf { it in 0..9 }?.toString() ?: "" }.joinToString("")
                latin.toIntOrNull()?.let { st.chooseTarget(it); onTarget() }; custom = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("إلغاء") } })
    }
    if (confirmAll) {
        AlertDialog(onDismissRequest = { confirmAll = false },
            title = { Text("تصفير كل العدّادات؟") },
            confirmButton = { TextButton(onClick = { st.resetAll(); confirmAll = false; onReset() }) { Text("تصفير", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun Chip(title: String, on: Boolean, warm: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) warm else Color.White.copy(alpha = 0.08f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(title, color = if (on) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Ready-made full-screen Activity: `TasbeehActivity.start(context)`. */
class TasbeehActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TasbeehScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, TasbeehActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
