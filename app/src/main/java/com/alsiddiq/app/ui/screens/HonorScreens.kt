package com.alsiddiq.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.components.*
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.theme.Brand
import java.time.LocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** «المتفوقون» periods: this month, this term (the last 3 months), all time. */
private val HonorPeriods = listOf("الشهر", "الفصل", "الكل")
private fun honorSince(i: Int, today: LocalDate = LocalDate.now()): LocalDate? = when (i) {
    0 -> today.withDayOfMonth(1)
    1 -> today.minusMonths(3)
    else -> null
}

/** The key score of the report, in one line: «١٢٫٥ صفحة حفظ • تقدير ٤٫٦/٥ • حضور ٩٢٪». */
fun honorScoreText(h: Stats.Honor): String =
    Fmt.one(h.hifzPages) + " صفحة حفظ • تقدير " + Fmt.one(h.avgGrade) + "/5" + (if (h.attendance > 0f) " • حضور " + Fmt.pct(h.attendance) else "")

/**
 * «المتفوقون»: ranked automatically from the same records as «التقارير» (new memorisation first, then grades and attendance).
 * Everyone sees it (read-only); the admin can «اظهر تفوق الطالب» (optional, any time) to put a congratulation card on everyone's screen.
 */
@Composable
fun HonorsScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    var period by remember { mutableIntStateOf(0) }
    var scope by remember { mutableIntStateOf(0) }   // 0 = the whole centre, then each circle
    val circles = remember(snap.circles) { snap.circles.sortedBy { it.name } }
    val circle = if (scope == 0) null else circles.getOrNull(scope - 1)
    val students = if (circle == null) snap.students else snap.students.filter { it.circleId == circle.id }
    val ranked = remember(snap, period, scope) { Stats.honors(snap, students, honorSince(period)) }
    var publishing by remember { mutableStateOf(false) }
    ScreenList("المتفوقون", onBack = { nav.popBackStack() }) {
        item { ChoiceChips(HonorPeriods, period, { period = it }) }
        item { ChoiceChips(listOf("المركز كله") + circles.map { it.name }, scope, { scope = it }) }
        if (user.role == Role.ADMIN && ranked.isNotEmpty()) item {
            Button(onClick = { publishing = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Gold, contentColor = Color.White)) {
                Icon(Icons.Outlined.EmojiEvents, null); Spacer(Modifier.width(6.dp)); Text("اظهر تفوق الطالب", fontWeight = FontWeight.SemiBold)
            }
        }
        if (ranked.isEmpty()) item { EmptyState("لا يوجد ترتيب بعد", "يظهر المتفوقون هنا من تسجيلات الحفظ والتسميع في هذه الفترة") }
        itemsIndexedHonor(ranked, snap, nav)
        snap.honorCards.firstOrNull()?.let { c ->
            item { SectionHeader("آخر تهنئة") }
            item { HonorCardSummary(c) }
        }
    }
    if (publishing) PublishHonorDialog(ranked, onDone = { publishing = false })
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedHonor(ranked: List<Stats.Honor>, snap: CenterSnapshot, nav: NavHostController) {
    ranked.forEachIndexed { i, h ->
        item(key = "h_" + h.student.id) {
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> null }
            AppCard(padding = 12.dp, onClick = { nav.navigate("student/${h.student.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                        if (medal != null) Text(medal, fontSize = 24.sp) else Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Brand.Muted)
                    }
                    Spacer(Modifier.width(6.dp))
                    StudentAvatar(h.student, size = if (i < 3) 50.dp else 42.dp, color = if (i < 3) Brand.Gold else Brand.Navy)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = if (i < 3) FontWeight.Bold else FontWeight.Medium)
                        Text(snap.circle(h.student.circleId)?.name ?: "بدون حلقة", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        Text(honorScoreText(h), style = MaterialTheme.typography.labelSmall, color = Brand.Navy)
                    }
                }
            }
        }
    }
}

@Composable
private fun HonorCardSummary(c: HonorCard) {
    AppCard {
        Text("🏆 " + c.students.joinToString("، ") { it.fullName }, style = MaterialTheme.typography.titleSmall)
        if (c.message.isNotBlank()) Text(c.message, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        Text(Fmt.relative(c.createdAt), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
    }
}

/** Admin: the top 3 are ticked already; untick or tick others, add a message, publish. */
@Composable
private fun PublishHonorDialog(ranked: List<Stats.Honor>, onDone: () -> Unit) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val picked = remember { mutableStateListOf<String>().apply { addAll(ranked.take(3).map { it.student.id }) } }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("اظهر تفوق الطالب") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("تظهر بطاقة تهنئة على شاشة الجميع مرة واحدة عند فتح التطبيق.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(8.dp))
                ranked.take(15).forEachIndexed { i, h ->
                    val on = h.student.id in picked
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (on) picked.remove(h.student.id) else picked.add(h.student.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { v -> if (v) picked.add(h.student.id) else picked.remove(h.student.id) })
                        Text("${i + 1}. ${h.student.fullName}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field(message, { message = it }, "رسالة (اختياري)", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = {
                val list = ranked.filter { it.student.id in picked }.map { h ->
                    HonorStudent(h.student.id, h.student.fullName, h.student.photoPath, snap.circle(h.student.circleId)?.name ?: "", honorScoreText(h))
                }
                actions.run("نُشرت التهنئة", { repo.publishHonorCard(list, message) }) { onDone() }
            }) { Text("نشر") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } }
    )
}

/**
 * The newest congratulation card, full screen, ONCE per phone (remembered here), in the style of the occasion greeting:
 * «🏆 مبارك للمتفوقين», the students, «شكراً لكم على تميّزكم» and the admin's message, and «شكراً» to close.
 */
@Composable
fun HonorPopupHost() {
    val ctx = LocalContext.current
    val snap = snapshot()
    val card = snap.honorCards.maxByOrNull { it.createdAt } ?: return
    val prefs = remember { ctx.getSharedPreferences("alsiddiq_honors", android.content.Context.MODE_PRIVATE) }
    var show by remember(card.id) { mutableStateOf(prefs.getString("seen", null) != card.id) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); ready = true }
    if (!show || !ready) return
    val close = { prefs.edit().putString("seen", card.id).apply(); show = false }
    val scale = remember { Animatable(0.82f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { fade.animateTo(1f, tween(380)) }
            scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().scale(scale.value).alpha(fade.value)
                .clip(RoundedCornerShape(28.dp)).background(Brand.Card)) {
                Box(Modifier.fillMaxWidth().height(132.dp).background(Brush.linearGradient(listOf(Brand.Navy, Brand.NavyMid, Brand.NavyDark))),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(58.dp).clip(CircleShape).background(Brand.Gold), contentAlignment = Alignment.Center) { Text("🏆", fontSize = 30.sp) }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brand.Gold))
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆 مبارك للمتفوقين", style = MaterialTheme.typography.headlineSmall, color = Brand.Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    card.students.forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val s = snap.student(h.id) ?: Student(h.id, h.fullName, circleId = null, joinedOn = "", photoPath = h.photoPath)
                            StudentAvatar(s, size = 46.dp, color = Brand.Gold)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.fullName, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                if (h.circleName.isNotBlank()) Text(h.circleName, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("شكراً لكم على تميّزكم", style = MaterialTheme.typography.titleMedium, color = Brand.Navy, textAlign = TextAlign.Center)
                    if (card.message.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(card.message, style = MaterialTheme.typography.bodyLarge, color = Brand.Muted, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = close, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Navy, contentColor = Color.White)) {
                        Text("شكراً", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
