@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.alsiddiq.app.R
import com.alsiddiq.app.core.Quran
import com.alsiddiq.app.data.BackendMode
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.components.*
import com.alsiddiq.app.ui.theme.Brand
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Step 1–2 of the teacher flow: pick the circle, then the student. */
@Composable
fun RecordStartScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val circles = Permissions.ownCircles(user, snap)   // v3.3: record in his own circle(s)
    var circleIdx by remember { mutableIntStateOf(0) }
    val circle = circles.getOrNull(circleIdx)
    val students = circle?.let { c -> snap.studentsIn(c.id).sortedBy { it.fullName } } ?: emptyList()
    val today = LocalDate.now()
    val logged = snap.recitations.filter { Stats.dayOf(it.recordedAt) == today }.map { it.studentId }.toSet()
    ScreenList("تسجيل تسميع", onBack = if (user.role == Role.ADMIN) ({ nav.popBackStack() }) else null) {
        if (RestDay.isRest(today)) item { AppCard(padding = 12.dp) { Text("اليوم الجمعة — يوم راحة", style = MaterialTheme.typography.titleSmall); Text(RestDay.MESSAGE, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) } }
        item {
            Text("١. اختر الحلقة", style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(circles.map { it.name }, circleIdx, { circleIdx = it })
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("٢. اختر الطالب", style = MaterialTheme.typography.labelLarge, color = Brand.Muted, modifier = Modifier.weight(1f))
                Text("سُجّل اليوم ${students.count { it.id in logged }}/${students.size}", style = MaterialTheme.typography.labelMedium, color = Brand.Green)
            }
        }
        if (circles.isEmpty()) item { EmptyState("لا توجد حلقات", "لم تُسند إليك حلقة بعد") }
        else if (students.isEmpty()) item { EmptyState("لا يوجد طلاب", "هذه الحلقة بلا طلاب بعد") }
        items(students, key = { it.id }) { s ->
            StudentRow(s, snap, onClick = { nav.navigate("record/${s.id}") }) {
                if (s.id in logged) Pill("✓ اليوم", Brand.Green) else Icon(Icons.Outlined.AddCircleOutline, null, tint = Brand.Maroon)
            }
        }
    }
}

private fun gradeTitle(kind: RecordKind): String = when (kind) {
    RecordKind.HIFZ -> "تقييم الحفظ"
    RecordKind.MURAJA -> "تقييم المراجعة"
    RecordKind.TASMEE -> "مراجعة وتسميع في نفس اليوم"
    RecordKind.AZFAR -> "تقييم أظافر لم يسمع"
    RecordKind.SARD -> "تقييم سرد السورة"
}

/** build 15: the recording row. «سرد السورة» takes the place of «مراجعة»; the old value stays in history only. */
private val RecordModes = listOf(RecordKind.HIFZ, RecordKind.TASMEE, RecordKind.SARD, RecordKind.AZFAR)

/** One rating row: the five Arabic grades, verbatim. */
@Composable
private fun RecordGradeRow(title: String, selected: Grade?, onPick: (Grade) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Grade.entries.forEach { g ->
            val sel = selected == g
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) gradeColor(g) else gradeColor(g).copy(alpha = 0.08f))
                    .clickable { onPick(g) },
                contentAlignment = Alignment.Center
            ) {
                Text(g.label, color = if (sel) Color.White else gradeColor(g), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Steps 3–5: surah + ayat, grade, save. Stats update immediately after saving. */
@Composable
fun RecordScreen(user: Member, nav: NavHostController, studentId: String) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val s = snap.student(studentId)
    if (s == null) {
        ScreenList("تسجيل", onBack = { nav.popBackStack() }) { item { EmptyState("الطالب غير موجود", "") } }
        return
    }
    val lastHifz = remember(studentId) {
        snap.recitations.filter { it.studentId == studentId && it.kind == RecordKind.HIFZ }.maxByOrNull { it.recordedAt }
    }
    // Suggest continuing right after the last memorised ayah.
    val start = remember(studentId) {
        lastHifz?.let {
            if (it.toAyah < Quran.ayahCount(it.toSurah)) it.toSurah to it.toAyah + 1
            else if (it.toSurah < 114) (it.toSurah + 1) to 1 else 1 to 1
        } ?: (1 to 1)
    }
    var kind by remember { mutableStateOf(RecordKind.HIFZ) }
    var fromSurah by remember { mutableIntStateOf(start.first) }
    var fromAyah by remember { mutableStateOf(start.second.toString()) }
    var toSurah by remember { mutableIntStateOf(start.first) }
    var toAyah by remember { mutableStateOf((start.second + 4).coerceAtMost(Quran.ayahCount(start.first)).toString()) }
    // v3.10 (build 13): each recording type keeps its own rating; the same-day row is the one under تسميع.
    val grades = remember { mutableStateMapOf<RecordKind, Grade>() }
    val grade = grades[kind]
    var mistakes by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // build 15: the مراجعة field (its own box, for any recording): null = not asked, true = راجع من … إلى, false = لم يراجع
    var reviewed by remember { mutableStateOf<Boolean?>(null) }
    var rFromSurah by remember { mutableIntStateOf(1) }
    var rFromAyah by remember { mutableStateOf("1") }
    var rToSurah by remember { mutableIntStateOf(1) }
    var rToAyah by remember { mutableStateOf("1") }
    var pickRFrom by remember { mutableStateOf(false) }
    var pickRTo by remember { mutableStateOf(false) }

    val fa = fromAyah.toIntOrNull() ?: 0
    val ta = toAyah.toIntOrNull() ?: 0
    val ayat = Quran.ayatBetween(fromSurah, fa, toSurah, ta)
    val rfa = rFromAyah.toIntOrNull() ?: 0
    val rta = rToAyah.toIntOrNull() ?: 0
    val reviewRangeOk = Quran.isValid(rFromSurah, rfa) && Quran.isValid(rToSurah, rta) && Quran.ayatBetween(rFromSurah, rfa, rToSurah, rta) > 0
    val reviewOk = reviewed != true || reviewRangeOk
    val valid = ayat > 0 && grade != null && reviewOk && !RestDay.isRest(LocalDate.now())   // v3.9: no recitation on Friday
    val pages = if (ayat > 0) Quran.pagesOf(Quran.globalIndex(fromSurah, fa), Quran.globalIndex(toSurah, ta)) else 0.0
    val surahLabel: (Int) -> String = { n -> "$n. ${Quran.name(n)} (${Quran.ayahCount(n)})" }

    val circleStudents = snap.studentsIn(s.circleId ?: "").sortedBy { it.fullName }
    val today = LocalDate.now()
    val loggedToday = snap.recitations.filter { Stats.dayOf(it.recordedAt) == today }.map { it.studentId }.toSet()
    val next = circleStudents.firstOrNull { it.id != s.id && it.id !in loggedToday }

    fun save(goNext: Boolean) {
        val g = grade ?: return
        saving = true
        // build 15: until the record has its own column, the مراجعة line rides at the top of the note.
        val reviewLine = when (reviewed) {
            true -> "المراجعة: من ${Quran.name(rFromSurah)} ${rfa} إلى ${Quran.name(rToSurah)} ${rta}"
            false -> "المراجعة: لم يراجع"
            null -> null
        }
        val noteAll = listOfNotNull(reviewLine, notes.trim().ifEmpty { null }).joinToString("\n")
        val r = Recitation("", s.id, s.circleId, user.id, kind, fromSurah, fa, toSurah, ta, g, mistakes, noteAll)
        actions.run("حُفظ: ${kind.label} $ayat آية — ${g.label}", { repo.logRecitation(r) }, onError = { saving = false }) {
            saving = false
            if (goNext && next != null) nav.navigate("record/${next.id}") { popUpTo("record/{sid}") { inclusive = true } }
            else nav.popBackStack()
        }
    }

    ScreenList("تسجيل لـ ${s.fullName}", onBack = { nav.popBackStack() }) {
        if (RestDay.isRest(today)) item { AppCard(padding = 12.dp) { Text("اليوم الجمعة — يوم راحة", style = MaterialTheme.typography.titleSmall); Text(RestDay.MESSAGE, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) } }
        item {
            AppCard(padding = 12.dp, onClick = { nav.navigate("student/${s.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(s)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "آخر حفظ: " + (lastHifz?.let { Stats.describeRange(it) + " • " + Fmt.relative(it.recordedAt) } ?: "لا يوجد"),
                            style = MaterialTheme.typography.bodySmall, color = Brand.Muted
                        )
                    }
                    Icon(Icons.Outlined.QueryStats, null, tint = Brand.Navy)
                }
            }
        }
        item {
            Text("نوع التسجيل", style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RecordModes.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k, onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, RecordModes.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = kindColor(k).copy(alpha = 0.15f), activeContentColor = kindColor(k))
                    ) { Text(k.label) }
                }
            }
        }
        item {
            AppCard {
                Text("المقطع", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Text("من", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectorField("السورة", Quran.name(fromSurah), Modifier.weight(1.6f)) { pickFrom = true }
                    Spacer(Modifier.width(8.dp))
                    Field(fromAyah, { fromAyah = it.filter { c -> c.isDigit() }.take(3) }, "الآية", Modifier.weight(1f), KeyboardType.Number)
                }
                Spacer(Modifier.height(10.dp))
                Text("إلى", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectorField("السورة", Quran.name(toSurah), Modifier.weight(1.6f)) { pickTo = true }
                    Spacer(Modifier.width(8.dp))
                    Field(toAyah, { toAyah = it.filter { c -> c.isDigit() }.take(3) }, "الآية", Modifier.weight(1f), KeyboardType.Number)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AssistChip(onClick = { toSurah = fromSurah; toAyah = Quran.ayahCount(fromSurah).toString() }, label = { Text("حتى نهاية السورة") })
                    AssistChip(onClick = { fromAyah = "1"; toSurah = fromSurah; toAyah = Quran.ayahCount(fromSurah).toString() }, label = { Text("السورة كاملة") })
                    AssistChip(onClick = {
                        val base = (toAyah.toIntOrNull() ?: fa)
                        toSurah = fromSurah; toAyah = (base + 5).coerceAtMost(Quran.ayahCount(fromSurah)).toString()
                    }, label = { Text("+5 آيات") })
                }
                Spacer(Modifier.height(8.dp))
                val msg = when {
                    !Quran.isValid(fromSurah, fa) -> "رقم آية البداية غير صحيح (السورة ${Quran.ayahCount(fromSurah)} آية)"
                    !Quran.isValid(toSurah, ta) -> "رقم آية النهاية غير صحيح (السورة ${Quran.ayahCount(toSurah)} آية)"
                    ayat <= 0 -> "نهاية المقطع قبل بدايته"
                    else -> null
                }
                if (msg != null) Text(msg, color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                else Row {
                    Pill("$ayat آية", Brand.Navy)
                    Spacer(Modifier.width(6.dp))
                    Pill("${Fmt.one(pages)} صفحة", Brand.Maroon)
                    Spacer(Modifier.width(6.dp))
                    Pill("الجزء ${Quran.juzOf(fromSurah, fa)}", Brand.Gold)
                }
            }
        }
        item {
            AppCard {
                Text("مراجعة", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true to "راجع من … إلى", false to "لم يراجع").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = reviewed == v, onClick = { reviewed = v },
                            shape = SegmentedButtonDefaults.itemShape(i, 2)
                        ) { Text(label) }
                    }
                }
                if (reviewed == true) {
                    Spacer(Modifier.height(10.dp))
                    Text("من", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectorField("السورة", Quran.name(rFromSurah), Modifier.weight(1.6f)) { pickRFrom = true }
                        Spacer(Modifier.width(8.dp))
                        Field(rFromAyah, { rFromAyah = it.filter { c -> c.isDigit() }.take(3) }, "الآية", Modifier.weight(1f), KeyboardType.Number)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("إلى", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectorField("السورة", Quran.name(rToSurah), Modifier.weight(1.6f)) { pickRTo = true }
                        Spacer(Modifier.width(8.dp))
                        Field(rToAyah, { rToAyah = it.filter { c -> c.isDigit() }.take(3) }, "الآية", Modifier.weight(1f), KeyboardType.Number)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!reviewRangeOk) Text("نهاية المراجعة قبل بدايتها أو رقم الآية غير صحيح", color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                    else Pill("${Quran.ayatBetween(rFromSurah, rfa, rToSurah, rta)} آية مراجعة", Brand.Maroon)
                }
            }
        }
        if (kind != RecordKind.TASMEE) item { RecordGradeRow(gradeTitle(kind), grade) { grades[kind] = it } }
        item {
            AppCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("عدد الأخطاء", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalIconButton(onClick = { if (mistakes > 0) mistakes-- }) { Icon(Icons.Filled.Remove, "ناقص") }
                    Text("$mistakes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp))
                    FilledTonalIconButton(onClick = { mistakes++ }) { Icon(Icons.Filled.Add, "زائد") }
                }
                Spacer(Modifier.height(8.dp))
                Field(notes, { notes = it }, "ملاحظة للطالب وولي الأمر (اختياري)", singleLine = false, minLines = 2)
            }
        }
        if (kind == RecordKind.TASMEE) item { RecordGradeRow(gradeTitle(kind), grade) { grades[kind] = it } }
        item {
            Button(
                onClick = { save(false) }, enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp))
                Text(if (grade == null) "اختر التقدير للحفظ" else "حفظ النتيجة", style = MaterialTheme.typography.titleMedium)
            }
            if (next != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { save(true) }, enabled = valid && !saving,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)
                ) { Text("حفظ والانتقال إلى ${next.fullName}") }
            }
        }
    }
    if (pickFrom) PickerDialog("سورة البداية", (1..114).toList(), surahLabel, {
        fromSurah = it; fromAyah = "1"
        if (toSurah < it) { toSurah = it; toAyah = Quran.ayahCount(it).coerceAtMost(5).toString() }
    }, { pickFrom = false }, searchable = true)
    if (pickTo) PickerDialog("سورة النهاية", (1..114).toList(), surahLabel, { toSurah = it; toAyah = Quran.ayahCount(it).toString() }, { pickTo = false }, searchable = true)
    if (pickRFrom) PickerDialog("سورة بداية المراجعة", (1..114).toList(), surahLabel, { rFromSurah = it; rFromAyah = "1" }, { pickRFrom = false }, searchable = true)
    if (pickRTo) PickerDialog("سورة نهاية المراجعة", (1..114).toList(), surahLabel, { rToSurah = it; rToAyah = Quran.ayahCount(it).toString() }, { pickRTo = false }, searchable = true)
}
