package com.alsalmi.buttons.mushaf

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val MsQuranFont = FontFamily(Font(R.font.amiri_quran))

internal class MsState(ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_mushaf", Context.MODE_PRIVATE)
    var lastPage by mutableIntStateOf(p.getInt("last", 0))
        private set
    var bookmark by mutableIntStateOf(p.getInt("bookmark", 0))
        private set
    var fontSize by mutableFloatStateOf(p.getFloat("font", 24f))
        private set
    fun setLast(v: Int) { lastPage = v; p.edit().putInt("last", v).apply() }
    fun toggleBookmark(page: Int): Boolean {
        bookmark = if (bookmark == page) 0 else page
        p.edit().putInt("bookmark", bookmark).apply()
        return bookmark == page
    }
    fun bigger() { fontSize = minOf(44f, fontSize + 2f); p.edit().putFloat("font", fontSize).apply() }
    fun smaller() { fontSize = maxOf(18f, fontSize - 2f); p.edit().putFloat("font", fontSize).apply() }
    fun surahOfPage(page: Int) = MsQuran.pages[(page - 1).coerceIn(0, 603)] / 1000
    fun juzOfPage(page: Int): Int { val k = MsQuran.pages[(page - 1).coerceIn(0, 603)]; return MsQuran.juzOf(k / 1000, k % 1000) }
}

/**
 * «المصحف» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 * @param startPage opens the reader directly at this page (1…604), optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafScreen(onBack: (() -> Unit)? = null, startPage: Int? = null) {
    val ctx = LocalContext.current
    val s = remember { MsState(ctx) }
    var reader by remember { mutableStateOf<Int?>(startPage?.coerceIn(1, 604)) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    MsTheme {
        val open = reader
        if (open != null) {
            MsReader(s, open, onClose = { reader = null })
            return@MsTheme
        }
        Scaffold(
            containerColor = MsC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("المصحف", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MsC.bg),
                )
            },
        ) { pad ->
            val q = MsText.norm(query.trim())
            val suras = (1..114).filter { n -> q.isEmpty() || (q.toIntOrNull()?.let { "$n".startsWith("$it") } ?: MsText.norm(MsQuran.suras[n - 1].first).contains(q)) }
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") {
                    val has = s.lastPage > 0
                    MsHero(
                        "المصحف",
                        if (has) "آخر قراءة: سورة ${MsQuran.suras[s.surahOfPage(s.lastPage) - 1].first}\nصفحة ${s.lastPage} · الجزء ${s.juzOfPage(s.lastPage)}" else "المصحف كامل برسم المصحف العثماني\n114 سورة · 604 صفحة",
                    ) {
                        Column(
                            Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).msPress { reader = if (has) s.lastPage else 1 },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            MsGlyph("book", Color.White, 28.dp)
                            Text(if (has) "تابع" else "ابدأ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                if (s.bookmark > 0) item(key = "bm") {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(MsC.soft).msPress { reader = s.bookmark }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        MsGlyph("ribbon", MsC.gold, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("العلامة: صفحة ${s.bookmark} — سورة ${MsQuran.suras[s.surahOfPage(s.bookmark) - 1].first}", color = MsC.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                item(key = "search") { MsSearch(query, { query = it }, "ابحث باسم السورة أو رقمها") }
                item(key = "tabs") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MsChip("السور", tab == 0) { tab = 0 }
                        MsChip("الأجزاء", tab == 1) { tab = 1 }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.clip(RoundedCornerShape(50)).background(MsC.card).border(0.6.dp, MsC.line, RoundedCornerShape(50)).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(54.dp)) {
                                if (jump.isEmpty()) Text("صفحة", color = MsC.text2, fontSize = 14.sp)
                                androidx.compose.foundation.text.BasicTextField(jump, { v -> jump = v.filter { it.isDigit() }.take(3) }, singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = MsC.text, fontSize = 15.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            IconButton(onClick = {
                                val p = jump.toIntOrNull()
                                if (p != null && p in 1..604) { reader = p; jump = "" } else hint = "اكتب رقم صفحة من 1 إلى 604"
                            }, modifier = Modifier.size(38.dp)) { Icon(Icons.Filled.KeyboardArrowLeft, "اذهب", tint = MsC.accent) }
                        }
                    }
                }
                if (hint.isNotEmpty()) item(key = "hint") { Text(hint, color = MsC.warn, fontSize = 13.sp) }
                if (tab == 0) {
                    if (suras.isEmpty()) item(key = "empty") {
                        MsEmpty("ما لقينا سورة بهالاسم", "جرّب جزء من الاسم، مثل: البقرة، يس، الملك، أو رقمها.") { Icon(Icons.Filled.Search, null, tint = MsC.accent, modifier = Modifier.size(34.dp)) }
                    }
                    items(suras, key = { "s$it" }) { n ->
                        val x = MsQuran.suras[n - 1]
                        val pg = MsQuran.page(n, 1)
                        MsRow(n, "سورة ${x.first}", "${if (x.third) "مكية" else "مدنية"} · ${x.second} آية", "ص $pg", if (s.lastPage > 0 && s.surahOfPage(s.lastPage) == n) "آخر قراءة" else null) { reader = pg }
                    }
                } else {
                    items(30, key = { "j$it" }) { i ->
                        val st = MsQuran.juz[i]
                        val pg = MsQuran.page(st.first, st.second)
                        MsRow(i + 1, "الجزء ${i + 1}", "يبدأ من سورة ${MsQuran.suras[st.first - 1].first}، آية ${st.second}", "ص $pg", null) { reader = pg }
                    }
                }
            }
        }
    }
}

@Composable
private fun MsRow(n: Int, title: String, sub: String, trail: String, tag: String?, onClick: () -> Unit) {
    MsCard(Modifier.msPress(onClick = onClick), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MsC.gradient), contentAlignment = Alignment.Center) {
                Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MsC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(sub, color = MsC.text2, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(trail, color = MsC.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (tag != null) Text(tag, color = MsC.gold, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

private data class MsSeg(val surah: Int, val header: Boolean, val text: String, val basmala: String? = null)

private fun msSegments(ayat: Array<Array<String>>, page: Int): List<MsSeg> {
    val out = mutableListOf<MsSeg>()
    val cur = StringBuilder(); var curS = 0
    for ((sr, a) in MsQuran.ayahsOnPage(page)) {
        if (sr != curS || a == 1) {
            if (cur.isNotEmpty()) { out.add(MsSeg(curS, false, cur.toString())); cur.clear() }
            curS = sr
            if (a == 1) out.add(MsSeg(sr, true, "", MsQuran.split(ayat, sr, a).first))
        }
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(MsQuran.split(ayat, sr, a).second).append(' ').append(MsQuran.marker(a))
    }
    if (cur.isNotEmpty()) out.add(MsSeg(curS, false, cur.toString()))
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MsReader(s: MsState, start: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ayat by produceState<Array<Array<String>>?>(null) { value = withContext(Dispatchers.IO) { MsQuran.load(ctx) } }
    val pager = rememberPagerState(initialPage = start - 1) { MsQuran.PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf("") }
    val page = pager.currentPage + 1
    LaunchedEffect(page) { s.setLast(page) }
    BackHandler(onBack = onClose)

    Scaffold(
        containerColor = MsC.paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("سورة ${MsQuran.suras[s.surahOfPage(page) - 1].first}", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") } },
                actions = {
                    TextButton(onClick = { s.smaller() }) { Text("أ-", color = MsC.accent, fontSize = 15.sp) }
                    TextButton(onClick = { s.bigger() }) { Text("أ+", color = MsC.accent, fontSize = 18.sp) }
                    IconButton(onClick = {
                        toast = if (s.toggleBookmark(page)) "حفظت العلامة على صفحة $page" else "أزلت العلامة"
                        scope.launch { delay(1600); toast = "" }
                    }) {
                        MsGlyph("ribbon", if (s.bookmark == page) MsC.gold else MsC.text2.copy(alpha = 0.5f), 24.dp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MsC.paper),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val data = ayat
            if (data == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = MsC.accent)
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { idx ->
                    MsPage(data, idx + 1, s.fontSize)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(MsC.card).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("الجزء ${s.juzOfPage(page)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MsC.text)
                Slider(
                    value = page.toFloat(), onValueChange = { v -> scope.launch { pager.scrollToPage(v.toInt() - 1) } },
                    valueRange = 1f..604f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = MsC.accent, activeTrackColor = MsC.accent, inactiveTrackColor = MsC.soft),
                )
                Text("$page / 604", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MsC.text)
            }
            AnimatedVisibility(toast.isNotEmpty(), Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Text(toast, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(MsC.gradient).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun MsPage(ayat: Array<Array<String>>, page: Int, fontSize: Float) {
    val segs = remember(page) { msSegments(ayat, page) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text("سورة ${MsQuran.suras[MsQuran.pages[page - 1] / 1000 - 1].first}", color = MsC.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$page", color = MsC.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        segs.forEach { g ->
            if (g.header) {
                Text("سورة ${MsQuran.suras[g.surah - 1].first}", color = MsC.accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MsC.soft).border(1.dp, MsC.gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(vertical = 10.dp))
                g.basmala?.let { b -> Text(b, fontFamily = MsQuranFont, fontSize = fontSize.sp, color = MsC.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            } else {
                Text(g.text, fontFamily = MsQuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 2.1f).sp, color = MsC.text, textAlign = TextAlign.Justify, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** Ready-made full-screen Activity: `MushafActivity.start(context)`. */
class MushafActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MushafScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MushafActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
