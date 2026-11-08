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

@Composable
fun StudentsScreen(user: Member, nav: NavHostController, circleFilter: String?) {
    val snap = snapshot()
    var q by remember { mutableStateOf("") }
    val circles = Permissions.visibleCircles(user, snap)
    var circleIdx by remember { mutableIntStateOf(0) }
    val options = listOf("الكل") + circles.map { it.name }
    val all = Permissions.visibleStudents(user, snap)
    val list = all.filter { s ->
        (circleIdx == 0 || s.circleId == circles.getOrNull(circleIdx - 1)?.id) &&
            (q.isBlank() || s.fullName.contains(q.trim()) || s.phone.contains(q.trim()))
    }
    ScreenList("الطلاب", fab = {
        if (user.role == Role.ADMIN) AddFab("طالب جديد") { nav.navigate("student_edit/new") }
        else if (user.role == Role.TEACHER && Permissions.ownCircles(user, snap).isNotEmpty()) AddFab("طالب جديد") { nav.navigate("student_edit/new?circle=${Permissions.ownCircles(user, snap).first().id}") }
    }) {
        item {
            OutlinedTextField(q, { q = it }, placeholder = { Text("بحث باسم الطالب") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(8.dp))
            ChoiceChips(options, circleIdx, { circleIdx = it })
        }
        item { Text("${list.size} طالب", style = MaterialTheme.typography.labelMedium, color = Brand.Muted) }
        if (list.isEmpty()) item { EmptyState("لا يوجد طلاب", if (q.isBlank()) "أضف أول طالب للمركز" else "لا نتائج مطابقة للبحث") }
        items(list, key = { it.id }) { s ->
            StudentRow(s, snap, onClick = { nav.navigate("student/${s.id}") }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Brand.Muted)
            }
        }
    }
}

@Composable
fun StudentDetailScreen(user: Member, nav: NavHostController, id: String) {
    val snap = snapshot()
    val s = snap.student(id)
    if (s == null) {
        ScreenList("الطالب", onBack = { nav.popBackStack() }) { item { EmptyState("الطالب غير موجود", "ربما حُذف أو ليست لديك صلاحية") } }
        return
    }
    val canRecord = Permissions.canRecordFor(user, s, snap)
    ScreenList(s.fullName, onBack = { nav.popBackStack() }, actions = {
        if (Permissions.canEditStudent(user, s, snap)) IconButton(onClick = { nav.navigate("student_edit/${s.id}") }) { Icon(Icons.Outlined.Edit, "تعديل") }
    }, fab = {
        if (canRecord) AddFab("تسجيل تسميع") { nav.navigate("record/${s.id}") }
    }) {
        if (user.role == Role.TEACHER && !canRecord) item { ReadOnlyNote("عرض فقط: الطالب في حلقة ${snap.circle(s.circleId)?.name ?: "أخرى"}، والتعديل لمعلمها وللإدارة.") }
        item { StudentPhotoCard(s, Permissions.canEditStudent(user, s, snap)) }
        studentDashboard(this, s, snap, nav, user)
    }
}

/** The student progress dashboard, shared by the student's own home, parents and staff. */
fun studentDashboard(scope: androidx.compose.foundation.lazy.LazyListScope, s: Student, snap: CenterSnapshot, nav: NavHostController, user: Member, hPad: androidx.compose.ui.unit.Dp = 0.dp) {
    with(scope) {
        padItem(hPad) {
            val stats = remember(s, snap) { Stats.forStudent(s, snap) }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(stats.goalProgress, size = 118.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.one(stats.juzEquivalent), style = MaterialTheme.typography.headlineSmall, color = Brand.Navy)
                            Text("من ${s.goalJuz} جزء", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("المحفوظ", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                        Text("${stats.memorisedAyat} آية • ${Fmt.one(stats.memorisedPages)} صفحة", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("آخر تسميع", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                        Text(stats.lastRecord?.let { Stats.describeRange(it) } ?: "لا يوجد بعد", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Pill(snap.circle(s.circleId)?.name ?: "بدون حلقة", Brand.Navy)
                            Spacer(Modifier.width(6.dp))
                            if (stats.streakDays > 1) Pill("🔥 ${stats.streakDays} أيام", Brand.Orange)
                        }
                    }
                }
            }
        }
        padItem(hPad) {
            val stats = remember(s, snap) { Stats.forStudent(s, snap) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("الحضور", if (stats.presentDays + stats.absentDays > 0) Fmt.pct(stats.attendanceRate) else "—", Icons.Outlined.EventAvailable, Brand.Green, Modifier.weight(1f), sub = "${stats.absentDays} غياب")
                StatTile("متوسط التقدير", if (stats.averageGrade > 0) Fmt.one(stats.averageGrade) + "/5" else "—", Icons.Outlined.StarOutline, Brand.Gold, Modifier.weight(1f))
                StatTile("النقاط", "${stats.points}", Icons.Outlined.EmojiEvents, Brand.Maroon, Modifier.weight(1f), onClick = { nav.navigate("rewards") })
            }
        }
        padItem(hPad) {
            val stats = remember(s, snap) { Stats.forStudent(s, snap) }
            AppCard {
                Text("الآيات المسجّلة يومياً", style = MaterialTheme.typography.titleSmall)
                Text("حفظ ومراجعة — آخر 14 يوماً (اضغط على عمود)", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(10.dp))
                BarChart(stats.dailyAyat.map { (d, n) -> BarDatum(shortDayLabel(d) + " " + d.dayOfMonth, n.toFloat(), "$n آية") })
                Spacer(Modifier.height(12.dp))
                Row {
                    MiniStat("حفظ (30 يوماً)", "${stats.hifzAyatLast30}", Modifier.weight(1f))
                    MiniStat("مراجعة (30 يوماً)", "${stats.murajaAyatLast30}", Modifier.weight(1f))
                    MiniStat("سجلات", "${stats.recordsLast30}", Modifier.weight(1f))
                }
            }
        }
        padItem(hPad) { SectionHeader("أحدث السجلات", "السجل الكامل") { nav.navigate("history/${s.id}") } }
        val recent = snap.recitations.filter { it.studentId == s.id }.sortedByDescending { it.recordedAt }.take(5)
        if (recent.isEmpty()) padItem(hPad) { EmptyState("لا توجد سجلات", "سيظهر هنا كل حفظ وتسميع ومراجعة") }
        recent.forEach { r -> padItem(hPad, r.id) { RecordRow(r, snap, showStudent = false) } }
        padItem(hPad) {
            SectionHeader("بيانات الطالب")
            AppCard {
                KeyValueRow("الحلقة", snap.circle(s.circleId)?.name ?: "—")
                KeyValueRow("المعلم", snap.teacherOf(snap.circle(s.circleId)) ?: "—")
                KeyValueRow("تاريخ الالتحاق", runCatching { Fmt.date(LocalDate.parse(s.joinedOn)) }.getOrDefault(s.joinedOn))
                if (s.birthYear != null) KeyValueRow("سنة الميلاد", "${s.birthYear}")
                KeyValueRow("الحالة", s.status.label)
            }
        }
    }
}

@Composable
fun StudentEditScreen(user: Member, nav: NavHostController, id: String, presetCircle: String? = null) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val existing = snap.student(id)
    var name by remember { mutableStateOf(existing?.fullName ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var birth by remember { mutableStateOf(existing?.birthYear?.toString() ?: "") }
    var circleId by remember { mutableStateOf(existing?.circleId ?: presetCircle) }
    var goal by remember { mutableStateOf((existing?.goalJuz ?: 5).toString()) }
    var status by remember { mutableStateOf(existing?.status ?: StudentStatus.ACTIVE) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var pickCircle by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    ScreenList(if (existing == null) "طالب جديد" else "تعديل بيانات الطالب", onBack = { nav.popBackStack() }) {
        item {
            AppCard {
                Field(name, { name = it }, "اسم الطالب الكامل")
                Spacer(Modifier.height(10.dp))
                Field(phone, { phone = it }, "جوال ولي الأمر (اختياري)", keyboard = KeyboardType.Phone)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(birth, { birth = it.filter { c -> c.isDigit() }.take(4) }, "سنة الميلاد", Modifier.weight(1f), KeyboardType.Number)
                    Field(goal, { goal = it.filter { c -> c.isDigit() }.take(2) }, "الهدف (أجزاء)", Modifier.weight(1f), KeyboardType.Number)
                }
                Spacer(Modifier.height(10.dp))
                SelectorField("الحلقة", snap.circle(circleId)?.name ?: "اختر الحلقة") { pickCircle = true }
                Spacer(Modifier.height(12.dp))
                Text("الحالة", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(StudentStatus.entries.map { it.label }, status.ordinal, { status = StudentStatus.entries[it] })
                Spacer(Modifier.height(10.dp))
                Field(notes, { notes = it }, "ملاحظات", singleLine = false, minLines = 2)
            }
        }
        item {
            Button(
                onClick = {
                    val st = (existing ?: Student("", "", circleId = null, joinedOn = LocalDate.now().toString())).copy(
                        fullName = name.trim(), phone = phone.filter { it.isDigit() }, birthYear = birth.toIntOrNull(),
                        circleId = circleId, goalJuz = (goal.toIntOrNull() ?: 5).coerceIn(1, 30), status = status, notes = notes.trim()
                    )
                    actions.run(if (existing == null) "تمت إضافة الطالب" else "تم الحفظ", { repo.saveStudent(st) }) { nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(if (existing == null) "إضافة الطالب" else "حفظ") }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                if (Permissions.canManageStudents(user)) TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف الطالب", color = Brand.Red) }
            }
        }
    }
    if (pickCircle) PickerDialog("اختر الحلقة", Permissions.ownCircles(user, snap), { it.name }, { circleId = it.id }, { pickCircle = false })
    if (askDelete && existing != null) ConfirmDialog(
        "حذف الطالب؟", "ستُحذف جميع سجلاته وحضوره ومكافآته نهائياً.", "حذف",
        { actions.run("حُذف الطالب", { repo.deleteStudent(existing.id) }) { if (!nav.popBackStack("students", false)) nav.popBackStack("home", false) } },
        { askDelete = false }, destructive = true
    )
}

@Composable
fun HistoryScreen(user: Member, nav: NavHostController, studentId: String?, onBack: (() -> Unit)?) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val s = snap.student(studentId)
    var kind by remember { mutableIntStateOf(0) }
    val kinds = listOf("الكل") + RecordKind.entries.map { it.label }
    val recs = snap.recitations.filter { it.studentId == studentId && (kind == 0 || it.kind == RecordKind.entries[kind - 1]) }
        .sortedByDescending { it.recordedAt }
    val canDelete = s != null && Permissions.canRecordFor(user, s, snap)
    ScreenList("سجل ${s?.fullName ?: ""}", onBack = onBack) {
        item { ChoiceChips(kinds, kind, { kind = it }) }
        if (recs.isEmpty()) item { EmptyState("لا توجد سجلات", "لم يُسجَّل شيء من هذا النوع بعد") }
        var lastDay: LocalDate? = null
        recs.forEach { r ->
            val d = Stats.dayOf(r.recordedAt)
            if (d != lastDay) {
                lastDay = d
                item(key = "h$d") { Text(Fmt.dayTitle(d), style = MaterialTheme.typography.labelLarge, color = Brand.Navy, modifier = Modifier.padding(top = 8.dp)) }
            }
            item(key = r.id) {
                RecordRow(r, snap, showStudent = false, onDelete = if (canDelete) ({ actions.run("حُذف السجل", { repo.deleteRecitation(r.id) }) }) else null)
            }
        }
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.padItem(hPad: androidx.compose.ui.unit.Dp, key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.padding(horizontal = hPad)) { content() } }
}
