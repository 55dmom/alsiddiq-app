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
fun CirclesScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val circles = Permissions.visibleCircles(user, snap)
    ScreenList(if (user.role == Role.TEACHER) "حلقاتي" else "الحلقات", fab = {
        if (Permissions.canManageCircles(user)) AddFab("حلقة جديدة") { nav.navigate("circle_edit/new") }
    }) {
        if (circles.isEmpty()) item {
            EmptyState(
                "لا توجد حلقات", if (user.role == Role.TEACHER) "لم تُسند إليك حلقة بعد، تواصل مع الإدارة" else "أنشئ أول حلقة للمركز",
                action = if (Permissions.canManageCircles(user)) "حلقة جديدة" else null,
                onAction = { nav.navigate("circle_edit/new") }
            )
        }
        items(circles, key = { it.id }) { c -> CircleCard(c, snap) { nav.navigate("circle/${c.id}") } }
    }
}

@Composable
fun CircleDetailScreen(user: Member, nav: NavHostController, id: String) {
    val snap = snapshot()
    val circle = snap.circle(id)
    if (circle == null) {
        ScreenList("الحلقة", onBack = { nav.popBackStack() }) { item { EmptyState("الحلقة غير موجودة", "ربما حُذفت") } }
        return
    }
    val students = snap.studentsIn(id).sortedBy { it.fullName }
    val canTeach = user.role == Role.ADMIN || Permissions.teachesCircle(user, circle)
    val ids = students.map { it.id }.toSet()
    val weekStart = LocalDate.now().minusDays(6)
    val weekRecs = snap.recitations.filter { it.studentId in ids && !Stats.dayOf(it.recordedAt).isBefore(weekStart) }
    val todayIso = LocalDate.now().toString()
    val loggedToday = snap.recitations.filter { it.studentId in ids && Stats.dayOf(it.recordedAt) == LocalDate.now() }.map { it.studentId }.toSet()

    ScreenList(circle.name, onBack = { nav.popBackStack() }, actions = {
        if (Permissions.canManageCircles(user)) IconButton(onClick = { nav.navigate("circle_edit/${circle.id}") }) { Icon(Icons.Outlined.Edit, "تعديل") }
    }) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(snap.member(circle.teacherId)?.fullName ?: "؟", 46.dp, Brand.Maroon)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(snap.member(circle.teacherId)?.fullName ?: "بدون معلم", style = MaterialTheme.typography.titleSmall)
                        Text(listOf(circle.level, circle.schedule).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    MiniStat("الطلاب", "${students.size}/${circle.capacity}", Modifier.weight(1f))
                    MiniStat("سجلات الأسبوع", "${weekRecs.size}", Modifier.weight(1f))
                    MiniStat("آيات الأسبوع", "${weekRecs.sumOf { Stats.ayatOf(it) }}", Modifier.weight(1f))
                }
            }
        }
        if (canTeach) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { nav.navigate("attendance/${circle.id}") }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.FactCheck, null); Spacer(Modifier.width(6.dp)); Text("تحضير اليوم")
                }
                OutlinedButton(onClick = { nav.navigate("circle_report/${circle.id}") }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Summarize, null); Spacer(Modifier.width(6.dp)); Text("تقرير اليوم")
                }
            }
        }
        item {
            SectionHeader(
                "الطلاب" + if (canTeach) " — اضغط لتسجيل التسميع" else "",
                if (Permissions.canAddStudentTo(user, circle.id, snap)) "إضافة طالب" else null
            ) { nav.navigate("student_edit/new?circle=${circle.id}") }
        }
        if (students.isEmpty()) item { EmptyState("لا يوجد طلاب في الحلقة", "أضف طلاباً من شاشة الطلاب") }
        items(students, key = { it.id }) { s ->
            val todayAtt = snap.attendance.firstOrNull { it.studentId == s.id && it.day == todayIso }
            StudentRow(s, snap, onClick = { nav.navigate(if (canTeach) "record/${s.id}" else "student/${s.id}") }) {
                Column(horizontalAlignment = Alignment.End) {
                    if (s.id in loggedToday) Pill("سُجّل ✓", Brand.Green)
                    else if (canTeach) Icon(Icons.Outlined.AddCircleOutline, "تسجيل", tint = Brand.Maroon)
                    if (todayAtt != null) { Spacer(Modifier.height(4.dp)); Pill(todayAtt.status.label, attendanceColor(todayAtt.status)) }
                    IconButton(onClick = { nav.navigate("student/${s.id}") }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.QueryStats, "ملف الطالب", tint = Brand.Navy, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Brand.Navy)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
    }
}

@Composable
fun CircleEditScreen(user: Member, nav: NavHostController, id: String) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val existing = snap.circle(id)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var teacherId by remember { mutableStateOf(existing?.teacherId) }
    var level by remember { mutableStateOf(existing?.level ?: "") }
    var schedule by remember { mutableStateOf(existing?.schedule ?: "") }
    var capacity by remember { mutableStateOf((existing?.capacity ?: 15).toString()) }
    var pickTeacher by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val levels = listOf("تمهيدي", "متوسط", "متقدم", "مراجعة", "إجازة")

    ScreenList(if (existing == null) "حلقة جديدة" else "تعديل الحلقة", onBack = { nav.popBackStack() }) {
        item {
            AppCard {
                Field(name, { name = it }, "اسم الحلقة")
                Spacer(Modifier.height(10.dp))
                SelectorField("المعلم", snap.member(teacherId)?.fullName ?: "اختر المعلم") { pickTeacher = true }
                Spacer(Modifier.height(12.dp))
                Text("المستوى", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(levels, levels.indexOf(level), { level = levels[it] })
                Spacer(Modifier.height(10.dp))
                Field(schedule, { schedule = it }, "الموعد (مثال: بعد العصر • السبت–الأربعاء)")
                Spacer(Modifier.height(10.dp))
                Field(capacity, { capacity = it.filter { ch -> ch.isDigit() }.take(3) }, "السعة القصوى", keyboard = KeyboardType.Number)
            }
        }
        item {
            Button(
                onClick = {
                    val c = (existing ?: Circle("", "", null)).copy(
                        name = name.trim(), teacherId = teacherId, level = level, schedule = schedule.trim(),
                        capacity = capacity.toIntOrNull() ?: 15
                    )
                    actions.run(if (existing == null) "تم إنشاء الحلقة" else "تم الحفظ", { repo.saveCircle(c) }) { nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(if (existing == null) "إنشاء الحلقة" else "حفظ") }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف الحلقة", color = Brand.Red) }
            }
        }
    }
    if (pickTeacher) PickerDialog("اختر المعلم", snap.teachers(), { it.fullName }, { teacherId = it.id }, { pickTeacher = false })
    if (askDelete && existing != null) ConfirmDialog(
        "حذف الحلقة؟", "سيبقى الطلاب وسجلاتهم، لكن يصبحون بدون حلقة.", "حذف",
        { actions.run("حُذفت الحلقة", { repo.deleteCircle(existing.id) }) { if (!nav.popBackStack("circles", false)) nav.popBackStack("home", false) } },
        { askDelete = false }, destructive = true
    )
}
