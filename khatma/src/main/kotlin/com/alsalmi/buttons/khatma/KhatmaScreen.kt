package com.alsalmi.buttons.khatma

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

internal val KhQuranFont = FontFamily(Font(R.font.amiri_quran))

/** Plan and progress, saved in SharedPreferences "alsalmi_khatma". */
internal class KhState(ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_khatma", Context.MODE_PRIVATE)
    var days by mutableIntStateOf(p.getInt("days", 0)); private set
    var startDay by mutableStateOf(p.getString("start", "")!!); private set
    var startPage by mutableIntStateOf(p.getInt("startPage", 1)); private set
    var pointer by mutableIntStateOf(p.getInt("pointer", 1)); private set
    var completed by mutableIntStateOf(p.getInt("completed", 0)); private set
    var fontSize by mutableFloatStateOf(p.getFloat("font", 24f)); private set
    val log = mutableStateMapOf<String, Int>().apply {
        p.getString("log", "")!!.split(';').filter { it.contains('=') }.forEach { val (k, v) = it.split('='); v.toIntOrNull()?.let { n -> put(k, n) } }
    }

    private fun save() {
        p.edit().putInt("days", days).putString("start", startDay).putInt("startPage", startPage).putInt("pointer", pointer)
            .putInt("completed", completed).putFloat("font", fontSize)
            .putString("log", log.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today: String get() = fmt.format(Date())
    val hasPlan get() = days > 0 && startDay.isNotEmpty()
    val finished get() = pointer > 604
    val dayNumber: Int get() = runCatching {
        TimeUnit.MILLISECONDS.toDays(fmt.parse(today)!!.time - fmt.parse(startDay)!!.time + 3600_000L).toInt() + 1
    }.getOrDefault(1)
    val totalPages get() = 604 - startPage + 1
    val readPages get() = maxOf(0, minOf(604, pointer - 1) - startPage + 1)
    val progress get() = readPages.toFloat() / maxOf(1, totalPages)
    val doneToday get() = log[today] ?: 0
    val dailyPages: Int get() {
        val remDays = maxOf(1, days - dayNumber + 1)
        val remPages = maxOf(0, 604 - pointer + 1 + doneToday)
        return maxOf(1, ceil(remPages.toDouble() / remDays).toInt())
    }
    val portion: IntRange get() { val a = minOf(604, pointer); return a..minOf(604, a + dailyPages - 1) }
    val expected get() = minOf(totalPages, (totalPages.toDouble() * minOf(days, dayNumber - 1) / maxOf(1, days)).toInt())

    fun begin(d: Int, start: Int) {
        days = maxOf(1, d); startPage = start.coerceIn(1, 604); pointer = startPage; startDay = today; log.clear(); save()
    }
    /** true when the khatma has just been completed */
    fun markDone(): Boolean {
        val r = portion
        pointer = r.last + 1
        log[today] = (log[today] ?: 0) + (r.last - r.first + 1)
        val done = finished
        if (done) completed += 1
        save()
        return done
    }
    fun undoToday() { val n = log[today] ?: return; pointer = maxOf(startPage, pointer - n); log.remove(today); save() }
    fun bigger() { fontSize = minOf(44f, fontSize + 2f); save() }
    fun smaller() { fontSize = maxOf(18f, fontSize - 2f); save() }
    fun juzText(pages: Int): String {
        val j = pages / 20.0
        return when {
            j in 0.95..1.05 -> "جزء تقريباً"
            j < 1 && j in 0.45..0.55 -> "نصف جزء تقريباً"
            j < 1 -> "أقل من جزء"
            else -> String.format(Locale.US, "%.1f", j) + " جزء تقريباً"
        }
    }
}

/**
 * «ختمتي» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatmaScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { KhState(ctx) }
    val view = LocalView.current
    var setup by remember { mutableStateOf(false) }
    var reader by remember { mutableStateOf<IntRange?>(null) }
    var celebrate by remember { mutableStateOf(false) }

    KhTheme {
        val rr = reader
        if (rr != null) {
            KhReader(s, rr, onClose = { reader = null }, onDone = { if (s.markDone()) celebrate = true; reader = null })
            return@KhTheme
        }
        Scaffold(
            containerColor = KhC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("ختمتي", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                    actions = { if (s.hasPlan) IconButton(onClick = { setup = true }) { Icon(Icons.Filled.Settings, "ختمة جديدة", tint = KhC.accent) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = KhC.bg),
                )
            },
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!s.hasPlan) {
                    KhHero("ختمتي", "حدّد كم يوم تبي تختم فيه،\nوأقسم لك القرآن ورد يومي") {
                        KhRing(0f, size = 76.dp) { KhGlyph("book", Color.White, 30.dp) }
                    }
                    KhCard {
                        KhEmpty("ما بدأت ختمة للحين", "اختر المدة (أسبوع، شهر، شهرين…) ويطلع لك كم صفحة وكم جزء تقرأ كل يوم، وتعلّم على ورد اليوم إذا خلصته.") {
                            Icon(Icons.Filled.DateRange, null, tint = KhC.accent, modifier = Modifier.size(36.dp))
                        }
                    }
                    BigButton("ابدأ ختمة") { setup = true }
                } else {
                    val pct = (s.progress * 100).toInt()
                    KhHero(
                        if (s.finished) "ختمت" else "اليوم ${minOf(s.dayNumber, s.days)} من ${s.days}",
                        "${s.readPages} من ${s.totalPages} صفحة\n" + if (s.completed > 0) "ختماتك: ${s.completed}" else "الله يعينك ويتقبّل",
                    ) {
                        KhRing(s.progress, size = 86.dp, stroke = 8.dp) {
                            AnimatedContent(pct, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "pct") {
                                Text("$it%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                    val diff = s.readPages - s.expected
                    val (stText, stColor) = when {
                        s.finished -> "ختمت، ما شاء الله" to KhC.good
                        diff >= 0 -> (if (diff > s.dailyPages) "متقدّم على الخطة" else "ماشي على الخطة") to KhC.good
                        else -> {
                            val bd = ceil(-diff.toDouble() / maxOf(1, s.totalPages / maxOf(1, s.days))).toInt()
                            "متأخر ${if (bd <= 1) "يوم" else "$bd أيام"} — ورد اليوم زاد شوي عشان تلحق" to KhC.warn
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(stColor))
                        Spacer(Modifier.width(8.dp))
                        Text(stText, color = stColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    if (!s.finished) TodayCard(s, onRead = { reader = s.portion }, onDone = { if (s.markDone()) celebrate = true; KhHaptics.success(view) })
                    else BigButton("ختمة جديدة") { setup = true }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("باقي", "${maxOf(0, 604 - s.pointer + 1)}", "صفحة", Modifier.weight(1f))
                        Stat("الأيام الباقية", "${maxOf(0, s.days - s.dayNumber + 1)}", "يوم", Modifier.weight(1f))
                        Stat("الورد اليومي", "${s.dailyPages}", "صفحة", Modifier.weight(1f))
                    }
                    val recent = s.log.entries.sortedByDescending { it.key }.take(7)
                    if (recent.isNotEmpty()) KhCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("آخر الأيام", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = KhC.text)
                            recent.forEach { e ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = KhC.good, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(e.key, color = KhC.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text("${e.value} صفحة", color = KhC.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (setup) KhSetupSheet(s, onDismiss = { setup = false })
        if (celebrate) AlertDialog(
            onDismissRequest = { celebrate = false },
            title = { Text("ما شاء الله، ختمت!") },
            text = { Text("تقبّل الله منك. هذي ختمتك رقم ${s.completed}.") },
            confirmButton = { TextButton(onClick = { celebrate = false; setup = true }) { Text("ختمة جديدة") } },
            dismissButton = { TextButton(onClick = { celebrate = false }) { Text("تمام") } },
        )
    }
}

@Composable
private fun BigButton(t: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KhC.gradient).khPress(onClick = onClick).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(t, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun Stat(t: String, v: String, u: String, modifier: Modifier) {
    KhCard(modifier, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t, fontSize = 12.sp, color = KhC.text2)
            Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KhC.text)
            Text(u, fontSize = 11.sp, color = KhC.text2)
        }
    }
}

@Composable
private fun TodayCard(s: KhState, onRead: () -> Unit, onDone: () -> Unit) {
    val r = s.portion
    val a = KhQuran.pages[r.first - 1]
    val endKey = if (r.last < 604) KhQuran.pages[r.last] else 114 * 1000 + 7
    val last = KhQuran.prev(endKey / 1000, endKey % 1000)
    KhCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.doneToday > 0) "ورد إضافي" else "ورد اليوم", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = KhC.text, modifier = Modifier.weight(1f))
                Text("${r.last - r.first + 1} صفحة · ${s.juzText(r.last - r.first + 1)}", color = KhC.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Endpoint("من", "سورة ${KhQuran.suras[a / 1000 - 1].first} ${a % 1000}", "ص ${r.first}", Modifier.weight(1f))
                KhGlyph("chevL", KhC.text2, 22.dp)
                Endpoint("إلى", "سورة ${KhQuran.suras[last.first - 1].first} ${last.second}", "ص ${r.last}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(KhC.soft).khPress(onClick = onRead).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text("اقرأ الورد", color = KhC.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(KhC.gradient).khPress(onClick = onDone).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text(if (s.doneToday > 0) "قرأته كذلك" else "قرأت ورد اليوم ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            AnimatedVisibility(s.doneToday > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = KhC.good, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("قرأت اليوم ${s.doneToday} صفحة", color = KhC.text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { s.undoToday() }) { Text("تراجع") }
                }
            }
        }
    }
}

@Composable
private fun Endpoint(t: String, sur: String, pg: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(KhC.bg).padding(12.dp)) {
        Text(t, fontSize = 12.sp, color = KhC.text2)
        Text(sur, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = KhC.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pg, fontSize = 12.sp, color = KhC.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KhSetupSheet(s: KhState, onDismiss: () -> Unit) {
    var d by remember { mutableIntStateOf(if (s.days > 0) s.days else 30) }
    var start by remember { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KhC.bg) {
        KhTheme {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("خلال كم يوم تختم؟", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = KhC.text)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(7, 10, 15, 20, 30, 40, 60, 90)) { n -> KhChip("$n يوم", d == n) { d = n } }
                }
                Counter("المدة", "$d يوم", { if (d > 1) d-- }, { if (d < 365) d++ })
                Counter("أبدأ من صفحة", "$start — سورة ${KhQuran.suras[KhQuran.pages[start - 1] / 1000 - 1].first}", { if (start > 1) start-- }, { if (start < 604) start++ })
                val per = ceil((604 - start + 1).toDouble() / d).toInt()
                KhCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ورد كل يوم", color = KhC.text, modifier = Modifier.weight(1f))
                        Text("$per صفحة · ${s.juzText(per)}", color = KhC.accent, fontWeight = FontWeight.Bold)
                    }
                }
                BigButton("ابدأ الختمة") { s.begin(d, start); onDismiss() }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Counter(t: String, v: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KhC.card).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, color = KhC.text2, fontSize = 12.sp)
            Text(v, color = KhC.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = minus) { Text("−", fontSize = 22.sp, color = KhC.accent) }
        IconButton(onClick = plus) { Icon(Icons.Filled.Add, "زيادة", tint = KhC.accent) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KhReader(s: KhState, range: IntRange, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ayat by produceState<Array<Array<String>>?>(null) { value = withContext(Dispatchers.IO) { KhQuran.load(ctx) } }
    val count = range.last - range.first + 1
    val pager = rememberPagerState { count }
    BackHandler(onBack = onClose)
    val page = range.first + pager.currentPage
    Scaffold(
        containerColor = KhC.paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("صفحة $page · ${pager.currentPage + 1} من $count", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") } },
                actions = {
                    TextButton(onClick = { s.smaller() }) { Text("أ-", color = KhC.accent, fontSize = 15.sp) }
                    TextButton(onClick = { s.bigger() }) { Text("أ+", color = KhC.accent, fontSize = 18.sp) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = KhC.paper),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val data = ayat
            if (data == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = KhC.accent)
            else HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i -> KhPage(data, range.first + i, s.fontSize) }
            AnimatedVisibility(pager.currentPage == count - 1, Modifier.align(Alignment.BottomCenter).padding(16.dp), enter = slideInVertically { it } + fadeIn(), exit = fadeOut()) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(KhC.gradient).khPress(onClick = onDone).padding(horizontal = 28.dp, vertical = 14.dp)) {
                    Text("خلصت الورد ✓", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class KhSeg(val surah: Int, val header: Boolean, val text: String, val basmala: String? = null)

private fun khSegments(ayat: Array<Array<String>>, page: Int): List<KhSeg> {
    val out = mutableListOf<KhSeg>()
    val cur = StringBuilder(); var curS = 0
    for ((sr, a) in KhQuran.ayahsOnPage(page)) {
        if (sr != curS || a == 1) {
            if (cur.isNotEmpty()) { out.add(KhSeg(curS, false, cur.toString())); cur.clear() }
            curS = sr
            if (a == 1) out.add(KhSeg(sr, true, "", KhQuran.split(ayat, sr, a).first))
        }
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(KhQuran.split(ayat, sr, a).second).append(' ').append(KhQuran.marker(a))
    }
    if (cur.isNotEmpty()) out.add(KhSeg(curS, false, cur.toString()))
    return out
}

@Composable
private fun KhPage(ayat: Array<Array<String>>, page: Int, fontSize: Float) {
    val segs = remember(page) { khSegments(ayat, page) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text("سورة ${KhQuran.suras[KhQuran.pages[page - 1] / 1000 - 1].first}", color = KhC.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$page", color = KhC.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        segs.forEach { g ->
            if (g.header) {
                Text("سورة ${KhQuran.suras[g.surah - 1].first}", color = KhC.accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KhC.soft).border(1.dp, KhC.gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(vertical = 10.dp))
                g.basmala?.let { b -> Text(b, fontFamily = KhQuranFont, fontSize = fontSize.sp, color = KhC.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            } else {
                Text(g.text, fontFamily = KhQuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 2.1f).sp, color = KhC.text, textAlign = TextAlign.Justify, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** Ready-made full-screen Activity: `KhatmaActivity.start(context)`. */
class KhatmaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KhatmaScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, KhatmaActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
