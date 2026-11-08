package com.alsalmi.buttons.adhkar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val AdkQuranFont = FontFamily(Font(R.font.amiri_quran))

internal enum class AdkSection(val title: String, val hint: String, val glyph: String) {
    SABAH("الصباح", "من بعد الفجر إلى طلوع الشمس", "sunrise"),
    MASA("المساء", "من بعد العصر إلى غروب الشمس", "moon"),
    SALAH("بعد الصلاة", "بعد السلام من الصلاة المفروضة", "dome"),
    NAWM("النوم", "عند الاضطجاع للنوم", "sleep");

    val items: List<AdkItem>
        get() = when (this) {
            SABAH -> AdhkarData.sabah
            MASA -> AdhkarData.masa
            SALAH -> AdhkarData.salah
            NAWM -> AdhkarData.nawm
        }

    companion object {
        fun now(): AdkSection {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when {
                h in 3..11 -> SABAH
                h in 15..20 -> MASA
                h >= 21 || h < 3 -> NAWM
                else -> SALAH
            }
        }
    }
}

/** Today's counts, saved in SharedPreferences "alsalmi_adhkar"; a new day starts fresh. */
internal class AdkState(ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_adhkar", Context.MODE_PRIVATE)
    val done = mutableStateMapOf<String, Int>()
    var fontSize by mutableStateOf(p.getFloat("font", 21f))
        private set
    var haptics by mutableStateOf(p.getBoolean("haptics", true))
        private set

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    init { load() }

    fun load() {
        done.clear()
        if (p.getString("day", "") == today()) {
            p.getString("done", "")!!.split(';').filter { it.contains('=') }.forEach {
                val (k, v) = it.split('=', limit = 2); v.toIntOrNull()?.let { n -> done[k] = n }
            }
        } else save()
    }

    private fun save() {
        p.edit().putString("day", today()).putString("done", done.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    fun count(x: AdkItem) = minOf(done[x.id] ?: 0, x.count)
    fun finished(x: AdkItem) = count(x) >= x.count

    /** true when this tap completed the item */
    fun tap(x: AdkItem): Boolean {
        if (p.getString("day", "") != today()) done.clear()
        val c = count(x)
        if (c >= x.count) return false
        done[x.id] = c + 1
        save()
        return c + 1 >= x.count
    }

    fun reset(s: AdkSection) { s.items.forEach { done.remove(it.id) }; save() }
    fun progress(s: AdkSection): Pair<Int, Int> = s.items.count { finished(it) } to s.items.size
    fun bigger() { fontSize = minOf(36f, fontSize + 2f); p.edit().putFloat("font", fontSize).apply() }
    fun smaller() { fontSize = maxOf(16f, fontSize - 2f); p.edit().putFloat("font", fontSize).apply() }
    fun flipHaptics() { haptics = !haptics; p.edit().putBoolean("haptics", haptics).apply() }
}

/**
 * «الأذكار» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { AdkState(ctx) }
    val view = LocalView.current
    var section by remember { mutableStateOf(AdkSection.now()) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var confirmReset by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    AdkTheme {
        Scaffold(
            containerColor = AdkC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("الأذكار", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                    },
                    actions = {
                        TextButton(onClick = { s.smaller() }) { Text("أ-", fontSize = 15.sp, color = AdkC.accent) }
                        TextButton(onClick = { s.bigger() }) { Text("أ+", fontSize = 18.sp, color = AdkC.accent) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AdkC.bg),
                )
            },
        ) { pad ->
            val items = section.items
            val prog = s.progress(section)
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "hero") {
                    AdkHero(
                        title = "أذكار ${section.title}",
                        subtitle = "${section.hint}\n${prog.first} من ${prog.second} مكتملة",
                    ) {
                        AdkRing(prog.first.toFloat() / maxOf(prog.second, 1), size = 74.dp) {
                            AnimatedContent(section, transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) }, label = "glyph") { sec ->
                                AdkGlyph(sec.glyph, Color.White, 30.dp)
                            }
                        }
                    }
                }
                item(key = "chips") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(AdkSection.entries.toList()) { sec ->
                            val pr = s.progress(sec)
                            AdkChip(sec.title + if (pr.first == pr.second) " ✓" else "", section == sec) {
                                section = sec
                                scope.launch { list.animateScrollToItem(0) }
                            }
                        }
                    }
                }
                item(key = "done-${section.name}") {
                    AnimatedVisibility(prog.first == prog.second, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        AdkCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = AdkC.good, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("أتممت أذكار ${section.title}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = AdkC.text)
                                    Text("تقبّل الله منك، وجعلها في ميزان حسناتك", fontSize = 14.sp, color = AdkC.text2)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(items, key = { _, x -> x.id }) { i, x ->
                    AdkItemCard(
                        x = x, index = i + 1, count = s.count(x), fin = s.finished(x), fontSize = s.fontSize,
                        open = open[x.id] == true, onToggle = { open[x.id] = open[x.id] != true },
                        onTap = {
                            if (!s.finished(x)) {
                                val completed = s.tap(x)
                                if (s.haptics) { if (completed) AdkHaptics.success(view) else AdkHaptics.tap(view) }
                                if (completed) {
                                    val next = items.indexOfFirst { !s.finished(it) }
                                    if (next >= 0) scope.launch { delay(350); list.animateScrollToItem(next + 3) }
                                }
                            }
                        },
                    )
                }
                item(key = "reset") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(AdkC.soft).adkPress { confirmReset = true }.padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Refresh, null, tint = AdkC.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة أذكار ${section.title}", color = AdkC.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                item(key = "haptics") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { s.flipHaptics() }) {
                            Text(if (s.haptics) "الاهتزاز: يعمل" else "الاهتزاز: متوقف", color = AdkC.text2, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("إعادة العدّ؟") },
                text = { Text("يرجع عدّاد أذكار ${section.title} إلى الصفر.") },
                confirmButton = { TextButton(onClick = { s.reset(section); confirmReset = false }) { Text("إعادة") } },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } },
            )
        }
    }
}

@Composable
private fun AdkItemCard(
    x: AdkItem, index: Int, count: Int, fin: Boolean, fontSize: Float,
    open: Boolean, onToggle: () -> Unit, onTap: () -> Unit,
) {
    val alpha by animateFloatAsState(if (fin) 0.72f else 1f, label = "fade")
    val border by animateColorAsState(if (fin) AdkC.good.copy(alpha = 0.55f) else Color.Unspecified, label = "border")
    AdkCard(Modifier.then(Modifier), border = if (fin) border else Color.Unspecified) {
        Column(Modifier.fillMaxWidth().then(Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (fin) AdkC.good.copy(alpha = 0.15f) else AdkC.soft), contentAlignment = Alignment.Center) {
                    Text("$index", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (fin) AdkC.good else AdkC.accent)
                }
                if (x.quran) {
                    Text("قرآن", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AdkC.gold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AdkC.gold.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(if (x.count > 1) "يُكرَّر ${x.count} مرات" else "مرة واحدة", fontSize = 12.sp, color = AdkC.text2, modifier = Modifier.weight(1f))
                AnimatedVisibility(fin, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AdkC.good, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.fillMaxWidth().clickable(enabled = !fin, onClick = onTap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (x.basmala) {
                    Text(AdhkarData.BASMALA, fontFamily = AdkQuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 2f).sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = AdkC.text.copy(alpha = alpha))
                }
                Text(
                    x.text,
                    fontFamily = if (x.quran) AdkQuranFont else FontFamily.Default,
                    fontSize = (if (x.quran) fontSize + 1 else fontSize).sp,
                    lineHeight = (fontSize * if (x.quran) 2.1f else 1.75f).sp,
                    color = AdkC.text.copy(alpha = alpha),
                )
            }
            if (x.reward.isNotEmpty()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(AdkC.gold))
                    Spacer(Modifier.width(8.dp))
                    Text(x.reward, fontSize = 13.sp, color = AdkC.text2, lineHeight = 20.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(AdkC.soft).adkPress(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = AdkC.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(x.ref, fontSize = 13.sp, color = AdkC.accent, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.adkPress(enabled = !fin, onClick = onTap)) {
                    AdkRing(count.toFloat() / maxOf(x.count, 1), size = 58.dp, stroke = 5.dp, onGradient = false) {
                        AnimatedContent(if (fin) -1 else count, transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        }, label = "count") { c ->
                            if (c < 0) AdkGlyph("check", AdkC.good, 22.dp)
                            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$c", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AdkC.text)
                                Text("من ${x.count}", fontSize = 9.sp, color = AdkC.text2)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AdkC.soft).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("التخريج: ${x.source}", fontSize = 13.sp, color = AdkC.text2, lineHeight = 20.sp)
                    Text("الحكم: ${x.grade}", fontSize = 13.sp, color = AdkC.text2, lineHeight = 20.sp)
                    if (x.note.isNotEmpty()) Text("ملاحظة: ${x.note}", fontSize = 13.sp, color = AdkC.text2, lineHeight = 20.sp)
                }
            }
        }
    }
}

/** Ready-made full-screen Activity: `AdhkarActivity.start(context)`. */
class AdhkarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdhkarScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AdhkarActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
