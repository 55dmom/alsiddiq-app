package com.alsalmi.buttons.tasbeeh

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** One bundled background: a looping 540p video, its poster and its ambient sound (all inside this module, offline). */
internal data class CineScene(val id: String, val name: String, val video: Int, val poster: Int, val sound: Int)

internal object Cine {
    val scenes = listOf(
        CineScene("storm_sea", "بحر وبرق", R.raw.tsb_bg_storm_sea, R.raw.tsb_poster_storm_sea, R.raw.tsb_amb_storm_sea),
        CineScene("moon_sea", "قمر على البحر", R.raw.tsb_bg_moon_sea, R.raw.tsb_poster_moon_sea, R.raw.tsb_amb_moon_sea),
        CineScene("desert_night", "ليل الصحراء", R.raw.tsb_bg_desert_night, R.raw.tsb_poster_desert_night, R.raw.tsb_amb_desert_night),
        CineScene("misty_mountains", "جبال وضباب", R.raw.tsb_bg_misty_mountains, R.raw.tsb_poster_misty_mountains, R.raw.tsb_amb_misty_mountains),
    )
    val amiri = FontFamily(Font(R.font.tsb_amiri))
    val tajawal = FontFamily(Font(R.font.tsb_tajawal_bold, FontWeight.Bold))
    val warm = Color(0xFFFA9E52)
    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("alsalmi_tasbeeh_cine", Context.MODE_PRIVATE)
    fun saved(ctx: Context): CineScene { val id = prefs(ctx).getString("scene", null); return scenes.firstOrNull { it.id == id } ?: scenes[0] }
    fun save(ctx: Context, s: CineScene) { prefs(ctx).edit().putString("scene", s.id).apply() }
    /** His request (Nov 8): every time the tool opens, the NEXT background with its own sound; remembered for the next open. */
    fun nextOnOpen(ctx: Context): CineScene {
        val id = prefs(ctx).getString("scene", null)
        val i = scenes.indexOfFirst { it.id == id }
        val s = if (id == null || i < 0) scenes[0] else scenes[(i + 1) % scenes.size]
        save(ctx, s); return s
    }
    fun num(n: Int) = n.toString()
    private val posters = HashMap<Int, ImageBitmap?>()
    fun poster(ctx: Context, s: CineScene): ImageBitmap? = posters.getOrPut(s.poster) {
        runCatching { ctx.resources.openRawResource(s.poster).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }
}

/** Test hooks (Robolectric renders): draw no background so frames can be composited over the real loops. */
object CineTest {
    @JvmField var noBackground = false
    @JvmField var posterOnly = false
    /** fixed (top, bottom) insets in dp for renders; null = the window's real insets */
    @JvmField var insetsDp: Pair<Int, Int>? = null
    /** the screen's own tap action (set while the screen is shown) */
    @JvmField var tap: (() -> Unit)? = null
}

/** Glass ticks (3 variants), a soft cue at the end of a round, and the scene's ambient loop (fades in). */
internal object CineAudio {
    private var ctxApp: Context? = null
    var muted: Boolean
        get() = ctxApp?.let { Cine.prefs(it).getBoolean("muted", false) } ?: false
        set(v) { ctxApp?.let { Cine.prefs(it).edit().putBoolean("muted", v).apply() }; if (v) pauseAmbient() else resumeAmbient() }
    private var pool: SoundPool? = null
    private var ticks = IntArray(0); private var tickN = 0; private var cue = 0
    private var amb: MediaPlayer? = null
    private var ambRes = 0
    private var active = false
    private val main = Handler(Looper.getMainLooper())
    fun init(ctx: Context) {
        ctxApp = ctx.applicationContext
        if (pool != null) return
        runCatching {
            pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build().also {
                ticks = intArrayOf(it.load(ctx, R.raw.tsb_tick1, 1), it.load(ctx, R.raw.tsb_tick2, 1), it.load(ctx, R.raw.tsb_tick3, 1))
                // His rule (Nov 8): no musical or melodic sounds — the end-of-round chime is not played (haptic only).
            }
        }
    }
    fun tap() { if (!muted && ticks.isNotEmpty()) runCatching { pool?.play(ticks[tickN++ % ticks.size], 0.7f, 0.7f, 1, 0, 0.96f + (0..8).random() / 100f) } }
    fun complete() { /* no chime (his rule: natural sounds only); the screen's own vibration marks the end */ }
    fun ambient(ctx: Context, res: Int) {
        ctxApp = ctx.applicationContext
        if (res == ambRes && amb != null) { active = true; if (!muted) runCatching { amb?.start() }; return }
        stopAmbient(); ambRes = res; active = true
        runCatching {
            val mp = MediaPlayer.create(ctx.applicationContext, res) ?: return
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            amb = mp
            if (!muted) { mp.start(); fadeIn(mp) }
        }
    }
    private fun fadeIn(mp: MediaPlayer, step: Int = 0) {
        if (amb !== mp) return
        val v = (step / 20f).coerceAtMost(1f) * 0.26f
        runCatching { mp.setVolume(v, v) }
        if (step < 20) main.postDelayed({ fadeIn(mp, step + 1) }, 60)
    }
    fun pauseAmbient() { runCatching { amb?.takeIf { it.isPlaying }?.pause() } }
    fun resumeAmbient() { if (active && !muted) runCatching { amb?.let { it.start(); fadeIn(it) } } }
    fun stopAmbient() { runCatching { amb?.release() }; amb = null; ambRes = 0 }
    fun leave() { active = false; stopAmbient() }
}

/** Looping, muted, centre-cropped video on a TextureView; pauses with the lifecycle. */
private class CinePlayer(val ctx: Context, val res: Int, val onFirstFrame: () -> Unit) : TextureView.SurfaceTextureListener {
    var view: TextureView? = null
    private var mp: MediaPlayer? = null
    private var surface: Surface? = null
    private var vw = 0; private var vh = 0
    var paused = false
    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        surface = Surface(st)
        runCatching {
            val p = MediaPlayer()
            ctx.resources.openRawResourceFd(res).use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            p.setSurface(surface); p.isLooping = true; p.setVolume(0f, 0f)
            p.setOnVideoSizeChangedListener { _, a, b -> vw = a; vh = b; crop() }
            p.setOnInfoListener { _, what, _ -> if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame(); false }
            p.setOnPreparedListener { if (!paused) it.start() }
            p.prepareAsync(); mp = p
        }
    }
    fun crop() {
        val v = view ?: return
        if (vw == 0 || vh == 0 || v.width == 0 || v.height == 0) return
        val s = max(v.width / vw.toFloat(), v.height / vh.toFloat())
        val m = Matrix(); m.setScale(vw * s / v.width, vh * s / v.height, v.width / 2f, v.height / 2f); v.setTransform(m)
    }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { crop() }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    fun pause() { paused = true; runCatching { mp?.takeIf { it.isPlaying }?.pause() } }
    fun resume() { paused = false; runCatching { mp?.start() } }
    fun release() { runCatching { mp?.release() }; mp = null; runCatching { surface?.release() }; surface = null }
}

/** Poster first, then the looping video fades in over it. */
@Composable
internal fun CineBackground(scene: CineScene) {
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var playing by remember(scene.id) { mutableStateOf(false) }
    val vAlpha = remember(scene.id) { Animatable(0f) }
    LaunchedEffect(playing) { if (playing) vAlpha.animateTo(1f, tween(700)) }
    val poster = remember(scene.id) { Cine.poster(ctx, scene) }
    Box(Modifier.fillMaxSize().background(Color(0xFF05040C))) {
        if (poster != null) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (!CineTest.posterOnly) {
            val main = remember { Handler(Looper.getMainLooper()) }
            val player = remember(scene.id) { CinePlayer(ctx, scene.video) { main.post { playing = true } } }
            DisposableEffect(player, owner) {
                val obs = LifecycleEventObserver { _, e ->
                    when (e) {
                        Lifecycle.Event.ON_PAUSE -> { player.pause(); CineAudio.pauseAmbient() }
                        Lifecycle.Event.ON_RESUME -> { player.resume(); CineAudio.resumeAmbient() }
                        else -> {}
                    }
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs); player.release() }
            }
            AndroidView({ c -> TextureView(c).also { it.surfaceTextureListener = player; it.isOpaque = false; player.view = it; it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> player.crop() } } },
                Modifier.fillMaxSize().alpha(vAlpha.value))
        }
    }
}

/** Frame clock in seconds (Compose frame time, so tests with a paused clock are deterministic). */
@Composable
internal fun rememberCineClock(): State<Double> {
    val s = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { s.doubleValue = it / 1e9 } }
    return s
}

/** Round dots of light drifting up in the lower part of the screen. */
internal object CineParticles {
    fun rnd(i: Int, salt: Int): Double {
        var x = (i.toLong() * 2654435761L + salt * 40503L) xor -0x61c8864680b583ebL
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L; x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L; x = x xor (x ushr 31)
        return ((x % 10000 + 10000) % 10000) / 10000.0
    }
    fun draw(d: DrawScope, t: Double, count: Int = 28) = with(d) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val s1 = rnd(i, 1); val s2 = rnd(i, 2); val s3 = rnd(i, 3)
            val speed = (5 + 9 * s2) * density
            val span = h * 0.62
            val m = ((s1 * span + t * speed) % span)
            val yy = h - (h * 0.04f + m.toFloat())
            val xx = w * s3.toFloat() + (sin(t * (0.3 + s1 * 0.4) + i) * 14 * density).toFloat()
            val fade = sin(PI * (m / span))
            val tw = 0.55 + 0.45 * sin(t * (1.2 + s2 * 1.6) + i * 1.7)
            val r = ((0.8 + 1.5 * s2) * density).toFloat()
            val a = (0.55 * fade * tw).toFloat().coerceIn(0f, 1f)
            val p = Offset(xx, yy)
            drawCircle(Brush.radialGradient(listOf(Color(0.85f, 0.95f, 1f, a * 0.35f), Color.Transparent), p, r * 3), r * 3, p)
            drawCircle(Color(0.92f, 0.97f, 1f, a), r, p)
        }
    }
}

/** Scene picker body (shown in a bottom sheet): the 4 bundled backgrounds as tall cards. */
@Composable
internal fun CineScenePicker(current: String, pick: (CineScene) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("الخلفية", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Cine.tajawal)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().heightIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Cine.scenes) { s ->
                val on = s.id == current
                Box(Modifier.height(200.dp).clip(RoundedCornerShape(18.dp)).border(if (on) 2.5.dp else 1.dp, if (on) Cine.warm else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .clickable { pick(s) }, contentAlignment = Alignment.BottomCenter) {
                    val bm = remember(s.id) { Cine.poster(ctx, s) }
                    if (bm != null) Image(bm, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize().background(Color.Black))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                    Text(s.name, Modifier.padding(bottom = 10.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Small round translucent button with a drawn icon. */
@Composable
internal fun CineGlassButton(icon: CineIcon, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.13f else 0.05f)).clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        CineIconView(icon, Color.White.copy(alpha = if (enabled) 1f else 0.35f), 18.dp)
    }
}
