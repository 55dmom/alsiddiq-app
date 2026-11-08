package com.alsalmi.buttons.hifz

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val HfQuranFont = FontFamily(Font(R.font.amiri_quran))

/** Memorisation state, saved in SharedPreferences "alsalmi_hifz". */
internal class HfState(ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_hifz", Context.MODE_PRIVATE)
    var surah by mutableIntStateOf(p.getInt("surah", 67).coerceIn(1, 114))
    var from by mutableIntStateOf(p.getInt("from", 1))
    var to by mutableIntStateOf(p.getInt("to", 5))
    var target by mutableIntStateOf(p.getInt("target", 5))
    var fontSize by mutableFloatStateOf(p.getFloat("font", 24f))
    val memorised = mutableStateListOf<Int>().apply { addAll(ints("mem")) }
    val weak = mutableStateListOf<Int>().apply { addAll(ints("weak")) }
    val repeats = mutableStateMapOf<Int, Int>().apply {
        p.getString("rep", "")!!.split(';').filter { it.contains('=') }.forEach { val (k, v) = it.split('='); val kk = k.toIntOrNull(); val vv = v.toIntOrNull(); if (kk != null && vv != null) put(kk, vv) }
    }
    init { clamp() }
    private fun ints(k: String) = p.getString(k, "")!!.split(',').mapNotNull { it.toIntOrNull() }
    fun save() {
        p.edit().putInt("surah", surah).putInt("from", from).putInt("to", to).putInt("target", target).putFloat("font", fontSize)
            .putString("mem", memorised.joinToString(",")).putString("weak", weak.joinToString(","))
            .putString("rep", repeats.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
    val count get() = HfQuran.suras[surah - 1].second
    fun clamp() { from = from.coerceIn(1, count); to = to.coerceIn(from, count) }
    fun pick(s: Int) { surah = s; from = 1; to = minOf(5, HfQuran.suras[s - 1].second); save() }
    fun key(a: Int) = HfQuran.key(surah, a)
    fun bump(k: Int): Boolean { val n = (repeats[k] ?: 0) + 1; repeats[k] = n; save(); return n == target }
    fun toggleMem(k: Int) { if (k in memorised) memorised.remove(k) else { memorised.add(k); weak.remove(k) }; save() }
    fun toggleWeak(k: Int) { if (k in weak) weak.remove(k) else weak.add(k); save() }
    fun memCount(s: Int) = memorised.count { it / 1000 == s }

    companion object {
        /** words of an ayah (pause marks stay attached to the word before them) */
        fun words(t: String): List<String> {
            val out = mutableListOf<String>()
            for (w in t.split(' ').filter { it.isNotEmpty() }) {
                val hasLetter = w.any { it.code in 0x0621..0x064A || it.code in 0x0671..0x06D3 }
                if (!hasLetter && out.isNotEmpty()) out[out.size - 1] = out.last() + " " + w else out.add(w)
            }
            return out
        }
    }
}

/**
 * «وضع الحفظ» — the button's single entry composable (the whole Mushaf).
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HifzScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { HfState(ctx) }
    val view = LocalView.current
    val ayat by produceState<Array<Array<String>>?>(null) { value = withContext(Dispatchers.IO) { HfQuran.load(ctx) } }
    var tab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }
    val revealed = remember { mutableStateListOf<Int>() }
    val shownWords = remember { mutableStateMapOf<Int, Int>() }
    var picker by remember { mutableStateOf(false) }
    var progQ by remember { mutableStateOf("") }
    var reciteKey by remember { mutableIntStateOf(0) }
    fun resetView() { revealed.clear(); shownWords.clear(); reciteKey++ }
    // «تسميع»: the words of the whole range, in order, for the mic.
    val recite = ayat?.let { data -> if (mode == 3) remember(s.surah, s.from, s.to, reciteKey) {
        HfReciteState((s.from..s.to).flatMap { a -> val k = s.key(a); HfState.words(HfQuran.split(data, s.surah, a).second).map { k to it } })
    } else null }

    HfTheme {
        Scaffold(
            containerColor = HfC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("وضع الحفظ", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                    actions = {
                        TextButton(onClick = { s.fontSize = maxOf(18f, s.fontSize - 2f); s.save() }) { Text("أ-", fontSize = 15.sp, color = HfC.accent) }
                        TextButton(onClick = { s.fontSize = minOf(42f, s.fontSize + 2f); s.save() }) { Text("أ+", fontSize = 18.sp, color = HfC.accent) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = HfC.bg),
                )
            },
        ) { pad ->
            val data = ayat
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "tabs") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HfChip("جلسة الحفظ", tab == 0) { tab = 0 }
                        HfChip("تقدّمي", tab == 1) { tab = 1 }
                        HfChip("المراجعة" + if (s.weak.isNotEmpty()) "  ${s.weak.size}" else "", tab == 2) { tab = 2 }
                    }
                }
                if (data == null) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HfC.accent) } }
                } else when (tab) {
                    0 -> {
                        val mem = s.memCount(s.surah)
                        item(key = "hero") {
                            HfHero("سورة ${HfQuran.suras[s.surah - 1].first}", "حفظت $mem من ${s.count} آية\nالآيات ${s.from} – ${s.to}") {
                                HfRing(mem.toFloat() / maxOf(1, s.count), size = 80.dp, stroke = 8.dp) {
                                    Text("${(mem * 100f / maxOf(1, s.count)).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }
                        item(key = "range") {
                            HfCard {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picker = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("السورة", color = HfC.text2, modifier = Modifier.weight(1f))
                                        Text("${s.surah}. ${HfQuran.suras[s.surah - 1].first}", color = HfC.accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                        Icon(Icons.Filled.ArrowDropDown, null, tint = HfC.accent)
                                    }
                                    HorizontalDivider(color = HfC.line)
                                    Counter("من آية", s.from, { s.from--; s.clamp(); s.save(); resetView() }, { s.from++; s.clamp(); s.save(); resetView() }, s.from > 1, s.from < s.count)
                                    Counter("إلى آية", s.to, { s.to--; s.clamp(); s.save(); resetView() }, { s.to++; s.clamp(); s.save(); resetView() }, s.to > s.from, s.to < s.count)
                                    HorizontalDivider(color = HfC.line)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("التكرار", color = HfC.text2, modifier = Modifier.weight(1f))
                                        listOf(3, 5, 7, 10, 20).forEach { n -> HfChip("$n", s.target == n) { s.target = n; s.save() } }
                                    }
                                }
                            }
                        }
                        item(key = "modes") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(listOf("قراءة", "إخفاء الآيات", "اختبار الكلمات", "تسميع")) { i, t -> HfChip(t, mode == i) { mode = i; resetView() } }
                            }
                        }
                        if (recite != null) item(key = "recite") { HfRecitePanel(recite) }
                        items((s.from..s.to).toList(), key = { "a${s.surah}_$it" }) { a ->
                            val k = s.key(a)
                            val sp = HfQuran.split(data, s.surah, a)
                            HfAyahCard(
                                s, a, k, sp.first, sp.second, mode, k in revealed, recite?.shownIn(k) ?: (shownWords[k] ?: 0),
                                missed = recite?.missedIn(k) ?: -1,
                                onTapText = {
                                    if (mode == 1) { if (k in revealed) revealed.remove(k) else revealed.add(k) }
                                    if (mode == 2) shownWords[k] = minOf(HfState.words(sp.second).size, (shownWords[k] ?: 0) + 1)
                                },
                                onWords = { shownWords[k] = it },
                                onBump = { if (s.bump(k)) HfHaptics.success(view) else HfHaptics.tap(view) },
                                onMem = { s.toggleMem(k); HfHaptics.success(view) },
                                onWeak = { s.toggleWeak(k) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (s.to < s.count) item(key = "next") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("المقطع التالي", color = HfC.accent, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.soft).hfPress { s.from = s.to + 1; s.to = minOf(s.count, s.to + 5); s.save(); resetView() }.padding(horizontal = 18.dp, vertical = 11.dp))
                            }
                        }
                    }
                    1 -> {
                        val full = (1..114).count { s.memCount(it) == HfQuran.suras[it - 1].second }
                        item(key = "phero") {
                            HfHero("محفوظك من القرآن", "${s.memorised.size} آية من 6236\n$full سورة كاملة") {
                                HfRing(s.memorised.size / 6236f, size = 84.dp, stroke = 8.dp) {
                                    Text(String.format(java.util.Locale.US, "%.1f%%", s.memorised.size * 100f / 6236f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                        item(key = "pq") { HfSearch(progQ, { progQ = it }, "ابحث عن سورة") }
                        if (s.memorised.isEmpty() && progQ.isEmpty()) item(key = "pe") {
                            HfCard { HfEmpty("ما علّمت شي محفوظ للحين", "في جلسة الحفظ اضغط «حفظتها» تحت أي آية، ويطلع تقدّمك هنا لكل سورة.") { HfGlyph("check", HfC.accent, 34.dp) } }
                        }
                        val q = HfText.norm(progQ.trim())
                        val list = (1..114).filter { q.isEmpty() || HfText.norm(HfQuran.suras[it - 1].first).contains(q) }
                            .sortedWith(compareBy({ if (s.memCount(it) > 0) 0 else 1 }, { it }))
                        items(list, key = { "p$it" }) { sr ->
                            val c = s.memCount(sr); val n = HfQuran.suras[sr - 1].second
                            val frac by animateFloatAsState(c.toFloat() / n, label = "bar")
                            HfCard(Modifier.hfPress { s.pick(sr); resetView(); tab = 0 }, padding = 12.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row {
                                        Text("$sr. سورة ${HfQuran.suras[sr - 1].first}", color = HfC.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text("$c / $n", color = if (c == n) HfC.good else HfC.text2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(HfC.soft)) {
                                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(HfC.gradient))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        item(key = "rhero") {
                            HfHero("المراجعة", if (s.weak.isEmpty()) "الآيات اللي تعلّمها «ضعيفة» تجي هنا" else "${s.weak.size} آية تحتاج مراجعة") {
                                HfRing(1f, size = 70.dp) { HfGlyph("book", Color.White, 26.dp) }
                            }
                        }
                        if (s.weak.isEmpty()) item(key = "re") {
                            HfCard { HfEmpty("ما عندك آيات ضعيفة", "إذا تلخبطت في آية وقت الحفظ اضغط «ضعيفة»، وتلقاها هنا تراجعها لحالها.") { HfGlyph("ribbon", HfC.warn, 34.dp) } }
                        }
                        items(s.weak.sorted(), key = { "w$it" }) { k ->
                            val sr = k / 1000; val a = k % 1000
                            HfCard(Modifier.animateItem()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("سورة ${HfQuran.suras[sr - 1].first} · آية $a", color = HfC.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(HfQuran.split(data, sr, a).second, fontFamily = HfQuranFont, fontSize = (s.fontSize - 2).sp, lineHeight = (s.fontSize * 1.9f).sp, color = HfC.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("اختبرني", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.gradient).hfPress { s.surah = sr; s.from = a; s.to = a; s.save(); resetView(); mode = 2; tab = 0 }.padding(horizontal = 14.dp, vertical = 9.dp))
                                        Spacer(Modifier.weight(1f))
                                        Text("ضبطتها ✓", color = HfC.good, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.good.copy(alpha = 0.12f)).hfPress { s.weak.remove(k); if (k !in s.memorised) s.memorised.add(k); s.save() }.padding(horizontal = 14.dp, vertical = 9.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (picker) HfPicker(s, onPick = { s.pick(it); resetView(); picker = false }, onDismiss = { picker = false })
    }
}

@Composable
private fun Counter(t: String, v: Int, minus: () -> Unit, plus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = HfC.text, modifier = Modifier.weight(1f))
        IconButton(onClick = minus, enabled = canMinus) { Text("−", fontSize = 22.sp, color = if (canMinus) HfC.accent else HfC.line) }
        Text("$v", fontWeight = FontWeight.Bold, color = HfC.text, modifier = Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = plus, enabled = canPlus) { Icon(Icons.Filled.Add, "زيادة", tint = if (canPlus) HfC.accent else HfC.line) }
    }
}

@Composable
private fun HfAyahCard(
    s: HfState, a: Int, k: Int, basmala: String?, text: String, mode: Int, revealed: Boolean, shown: Int,
    onTapText: () -> Unit, onWords: (Int) -> Unit, onBump: () -> Unit, onMem: () -> Unit, onWeak: () -> Unit, modifier: Modifier = Modifier,
    missed: Int = -1,
) {
    val mem = k in s.memorised
    val weak = k in s.weak
    val reps = s.repeats[k] ?: 0
    val words = remember(text) { HfState.words(text) }
    val fs = s.fontSize
    HfCard(modifier, border = if (reps >= s.target && !mem) HfC.accent.copy(alpha = 0.6f) else Color.Unspecified) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("آية $a", color = HfC.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.soft).padding(horizontal = 10.dp, vertical = 4.dp))
                if (mem) Text("✓ محفوظة", color = HfC.good, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (weak) Text("للمراجعة", color = HfC.warn, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            if (basmala != null) Text(basmala, fontFamily = HfQuranFont, fontSize = (fs - 2).sp, color = HfC.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onTapText)) {
                when (mode) {
                    0 -> Text("$text ${HfQuran.marker(a)}", fontFamily = HfQuranFont, fontSize = fs.sp, lineHeight = (fs * 2.1f).sp, color = HfC.text)
                    1 -> AnimatedContent(revealed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reveal") { r ->
                        if (r) Text("$text ${HfQuran.marker(a)}", fontFamily = HfQuranFont, fontSize = fs.sp, lineHeight = (fs * 2.1f).sp, color = HfC.text)
                        else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(text, fontFamily = HfQuranFont, fontSize = fs.sp, lineHeight = (fs * 2.1f).sp, color = Color.Transparent,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HfC.soft))
                            Text("اضغط لإظهار الآية", color = HfC.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.card).padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    else -> {
                        val t = buildAnnotatedString {
                            words.forEachIndexed { i, w ->
                                if (i > 0) append(' ')
                                if (i < shown) append(w)
                                else if (i == missed) withStyle(SpanStyle(color = Color.Transparent, textDecoration = TextDecoration.Underline, background = HfC.warn.copy(alpha = 0.28f))) { append(w) }
                                else withStyle(SpanStyle(color = Color.Transparent, textDecoration = TextDecoration.Underline, background = HfC.soft)) { append(w) }
                            }
                            if (shown >= words.size) append(" " + HfQuran.marker(a))
                        }
                        Text(t, fontFamily = HfQuranFont, fontSize = fs.sp, lineHeight = (fs * 2.1f).sp, color = HfC.text)
                    }
                }
            }
            if (mode == 3) Text("$shown/${words.size}", color = HfC.text2, fontSize = 12.sp)
            if (mode == 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small("كلمة +") { onWords(minOf(words.size, shown + 1)) }
                    Small("الآية") { onWords(words.size) }
                    Small("من جديد") { onWords(0) }
                    Spacer(Modifier.weight(1f))
                    Text("$shown/${words.size}", color = HfC.text2, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.hfPress(onClick = onBump)) {
                    HfRing(minOf(reps, s.target).toFloat() / s.target, size = 54.dp, stroke = 5.dp, onGradient = false) {
                        AnimatedContent(reps, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "rep") { r ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$r", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = HfC.text)
                                Text("تكرار", fontSize = 8.sp, color = HfC.text2)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(if (weak) "شلها من المراجعة" else "ضعيفة", color = HfC.warn, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.warn.copy(alpha = 0.12f)).hfPress(onClick = onWeak).padding(horizontal = 12.dp, vertical = 9.dp))
                Text(if (mem) "✓ محفوظة" else "حفظتها", color = if (mem) HfC.bg else HfC.good, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (mem) HfC.good else HfC.good.copy(alpha = 0.12f)).hfPress(onClick = onMem).padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun Small(t: String, onClick: () -> Unit) {
    Text(t, color = HfC.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(HfC.soft).hfPress(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HfPicker(s: HfState, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HfC.bg) {
        HfTheme {
            val n = HfText.norm(q.trim())
            val xs = (1..114).filter { n.isEmpty() || (n.toIntOrNull()?.let { x -> "$it".startsWith("$x") } ?: HfText.norm(HfQuran.suras[it - 1].first).contains(n)) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HfSearch(q, { q = it }, "اسم السورة أو رقمها") }
                if (xs.isEmpty()) item { HfEmpty("ما لقينا السورة", "جرّب جزء من الاسم أو رقمها.") { Icon(Icons.Filled.Search, null, tint = HfC.accent, modifier = Modifier.size(34.dp)) } }
                items(xs, key = { it }) { sr ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sr == s.surah) HfC.soft else HfC.card).clickable { onPick(sr) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$sr. سورة ${HfQuran.suras[sr - 1].first}", color = HfC.text, modifier = Modifier.weight(1f))
                        Text("${s.memCount(sr)}/${HfQuran.suras[sr - 1].second}", color = HfC.text2, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Ready-made full-screen Activity: `HifzActivity.start(context)`. */
class HifzActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HifzScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, HifzActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
