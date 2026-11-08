package com.alsalmi.buttons.duas

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
import androidx.compose.foundation.text.selection.SelectionContainer
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

internal val DuaQuranFont = FontFamily(Font(R.font.amiri_quran))

internal enum class DuaFilter(val title: String) { ALL("الكل"), QURAN("من القرآن"), SUNNAH("من السنة"), FAV("المفضلة") }

internal object DuaText {
    /** Removes harakat / Qur'anic marks and unifies letter forms, for search. */
    fun norm(s: String): String {
        val b = StringBuilder()
        for (c in s) {
            val v = c.code
            if (v in 0x064B..0x065F || v == 0x0670 || v in 0x06D6..0x06ED || v == 0x0640) continue
            b.append(
                when (v) {
                    0x0622, 0x0623, 0x0625, 0x0671 -> 'ا'
                    0x0629 -> 'ه'
                    0x0649, 0x0626 -> 'ي'
                    0x0624 -> 'و'
                    else -> c
                }
            )
        }
        return b.toString()
    }
}

internal class DuaState(ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_duas", Context.MODE_PRIVATE)
    val favs = mutableStateListOf<String>().apply { addAll(p.getStringSet("favs", emptySet())!!.toList()) }
    var fontSize by mutableStateOf(p.getFloat("font", 21f))
        private set
    private val index = DuasData.all.associate { it.id to DuaText.norm(it.text + " " + it.cat + " " + it.ref) }

    fun items(f: DuaFilter, q: String): List<DuaItem> {
        var xs = when (f) {
            DuaFilter.ALL -> DuasData.all
            DuaFilter.QURAN -> DuasData.all.filter { it.quran }
            DuaFilter.SUNNAH -> DuasData.all.filter { !it.quran }
            DuaFilter.FAV -> DuasData.all.filter { it.id in favs }
        }
        val n = DuaText.norm(q.trim())
        if (n.isNotEmpty()) xs = xs.filter { index[it.id]!!.contains(n) }
        return xs
    }
    fun count(f: DuaFilter) = when (f) {
        DuaFilter.ALL -> DuasData.all.size
        DuaFilter.QURAN -> DuasData.all.count { it.quran }
        DuaFilter.SUNNAH -> DuasData.all.count { !it.quran }
        DuaFilter.FAV -> favs.size
    }
    fun toggleFav(id: String) {
        if (id in favs) favs.remove(id) else favs.add(id)
        p.edit().putStringSet("favs", favs.toSet()).apply()
    }
    fun bigger() { fontSize = minOf(36f, fontSize + 2f); p.edit().putFloat("font", fontSize).apply() }
    fun smaller() { fontSize = maxOf(16f, fontSize - 2f); p.edit().putFloat("font", fontSize).apply() }
}

/**
 * «الأدعية» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuasScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { DuaState(ctx) }
    val view = LocalView.current
    var filter by remember { mutableStateOf(DuaFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var toast by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DuaTheme {
        Scaffold(
            containerColor = DuaC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("الأدعية", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                    },
                    actions = {
                        TextButton(onClick = { s.smaller() }) { Text("أ-", fontSize = 15.sp, color = DuaC.accent) }
                        TextButton(onClick = { s.bigger() }) { Text("أ+", fontSize = 18.sp, color = DuaC.accent) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DuaC.bg),
                )
            },
        ) { pad ->
            val xs = s.items(filter, query)
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "hero") {
                        DuaHero("الأدعية", "${s.count(DuaFilter.QURAN)} دعاء من القرآن، و${s.count(DuaFilter.SUNNAH)} من السنة الصحيحة\nكل دعاء معه مصدره") {
                            Box(Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                DuaGlyph("book", Color.White, 32.dp)
                            }
                        }
                    }
                    item(key = "search") { DuaSearch(query, { query = it }, "ابحث في الأدعية: كرب، سفر، مطر…") }
                    item(key = "chips") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DuaFilter.entries.toList()) { f ->
                                DuaChip("${f.title}  ${s.count(f)}", filter == f) { filter = f }
                            }
                        }
                    }
                    if (xs.isEmpty()) {
                        item(key = "empty") {
                            if (filter == DuaFilter.FAV && query.isBlank())
                                DuaEmpty("ما عندك مفضلة للحين", "اضغط على القلب تحت أي دعاء يعجبك، ويصير هنا قريب منك.") {
                                    Icon(Icons.Filled.FavoriteBorder, null, tint = DuaC.accent, modifier = Modifier.size(36.dp))
                                }
                            else
                                DuaEmpty("ما لقينا شي", "جرّب كلمة ثانية، مثل: الهم، السفر، المطر، المسجد.") {
                                    Icon(Icons.Filled.Search, null, tint = DuaC.accent, modifier = Modifier.size(36.dp))
                                }
                        }
                    }
                    items(xs, key = { it.id }) { x ->
                        DuaCardItem(
                            x, fav = x.id in s.favs, open = open[x.id] == true, fontSize = s.fontSize,
                            onFav = { s.toggleFav(x.id); DuaHaptics.tap(view) },
                            onToggle = { open[x.id] = open[x.id] != true },
                            onCopy = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("دعاء", x.text + "\n— " + x.ref))
                                DuaHaptics.success(view)
                                toast = "تم النسخ"
                                scope.launch { delay(1600); toast = "" }
                            },
                            onShare = {
                                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, x.text + "\n— " + x.ref) }
                                ctx.startActivity(Intent.createChooser(i, "مشاركة").apply { if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                AnimatedVisibility(toast.isNotEmpty(), Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Text(toast, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(DuaC.gradient).padding(horizontal = 18.dp, vertical = 11.dp))
                }
            }
        }
    }
}

@Composable
private fun DuaCardItem(
    x: DuaItem, fav: Boolean, open: Boolean, fontSize: Float,
    onFav: () -> Unit, onToggle: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier,
) {
    val heart by animateFloatAsState(if (fav) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "heart")
    DuaCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (x.quran) "قرآن" else x.cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (x.quran) DuaC.gold else DuaC.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (x.quran) DuaC.gold.copy(alpha = 0.15f) else DuaC.soft).padding(horizontal = 10.dp, vertical = 4.dp))
                if (x.count > 1) Text("${x.count} مرات", fontSize = 12.sp, color = DuaC.text2)
                Spacer(Modifier.weight(1f))
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة",
                    tint = if (fav) Color(0xFFE11D48) else DuaC.text2,
                    modifier = Modifier.size(24.dp).scale(heart).duaPress(onClick = onFav))
            }
            SelectionContainer {
                Text(x.text, fontFamily = if (x.quran) DuaQuranFont else FontFamily.Default,
                    fontSize = (if (x.quran) fontSize + 1 else fontSize).sp,
                    lineHeight = (fontSize * if (x.quran) 2.1f else 1.75f).sp, color = DuaC.text)
            }
            if (x.reward.isNotEmpty()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(DuaC.gold))
                    Spacer(Modifier.width(8.dp))
                    Text(x.reward, fontSize = 13.sp, color = DuaC.text2, lineHeight = 20.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Row(
                    Modifier.clip(RoundedCornerShape(50)).background(DuaC.soft).duaPress(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = DuaC.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(x.ref, fontSize = 13.sp, color = DuaC.accent, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } }
                RoundAction("نسخ", onCopy) { DuaGlyph("copy", DuaC.accent, 18.dp) }
                RoundAction("مشاركة", onShare) { Icon(Icons.Filled.Share, null, tint = DuaC.accent, modifier = Modifier.size(18.dp)) }
            }
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DuaC.soft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المصدر: ${x.source}", fontSize = 13.sp, color = DuaC.text2, lineHeight = 20.sp)
                    Text("الحكم: ${x.grade}", fontSize = 13.sp, color = DuaC.text2, lineHeight = 20.sp)
                    if (x.note.isNotEmpty()) Text("ملاحظة: ${x.note}", fontSize = 13.sp, color = DuaC.text2, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(DuaC.soft).duaPress(onClick = onClick), contentAlignment = Alignment.Center) { icon() }
}

/** Ready-made full-screen Activity: `DuasActivity.start(context)`. */
class DuasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DuasScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DuasActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
