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
fun AttendanceScreen(user: Member, nav: NavHostController, circleId: String?) {
    if (user.role == Role.VIEWER) { AttendanceHistory(user, nav); return }
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    // v3.3: a teacher takes attendance in his own circle(s) only.
    val circles = Permissions.ownCircles(user, snap)
    var circleIdx by remember { mutableIntStateOf(circles.indexOfFirst { it.id == circleId }.coerceAtLeast(0)) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val circle = circles.getOrNull(circleIdx)
    val students = circle?.let { c -> snap.studentsIn(c.id).sortedBy { it.fullName } } ?: emptyList()
    val dayIso = day.toString()
    val saved = snap.attendance.filter { it.day == dayIso && it.circleId == circle?.id }.associate { it.studentId to it.status }
    val marks = remember(circle?.id, dayIso, saved) { mutableStateMapOf<String, AttendanceStatus>().apply { putAll(saved) } }
    val dirty = students.any { marks[it.id] != saved[it.id] }

    ScreenList("الحضور والغياب", onBack = if (user.role == Role.ADMIN) ({ nav.popBackStack() }) else null) {
        item { ChoiceChips(circles.map { it.name }, circleIdx, { circleIdx = it }) }
        item {
            AppCard(padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "اليوم السابق") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.dayTitle(day), style = MaterialTheme.typography.titleSmall)
                        Text(Fmt.hijri(day), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    IconButton(onClick = { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) }, enabled = day.isBefore(LocalDate.now())) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "اليوم التالي")
                    }
                }
            }
        }
        item {
            val p = students.count { marks[it.id] == AttendanceStatus.PRESENT || marks[it.id] == AttendanceStatus.LATE }
            val a = students.count { marks[it.id] == AttendanceStatus.ABSENT }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("حاضر $p", Brand.Green); Spacer(Modifier.width(6.dp))
                Pill("غائب $a", Brand.Red); Spacer(Modifier.width(6.dp))
                Pill("لم يُحدَّد ${students.count { marks[it.id] == null }}", Brand.Muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { students.forEach { if (marks[it.id] == null) marks[it.id] = AttendanceStatus.PRESENT } }) { Text("البقية حاضرون") }
            }
        }
        if (students.isEmpty()) item { EmptyState("لا يوجد طلاب", "اختر حلقة فيها طلاب") }
        items(students, key = { it.id }) { s ->
            AppCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(s, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.fullName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (isPending(com.alsiddiq.app.domain.SyncPolicy.attendanceKey(s.id, dayIso))) PendingChip()
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttendanceStatus.entries.forEach { st ->
                        val sel = marks[s.id] == st
                        val col = attendanceColor(st)
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) col else col.copy(alpha = 0.08f))
                                .clickable { marks[s.id] = st },
                            contentAlignment = Alignment.Center
                        ) { Text(st.label, color = if (sel) Color.White else col, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        if (circle != null && students.isNotEmpty()) item {
            Button(
                onClick = {
                    val m = students.mapNotNull { s -> marks[s.id]?.let { s.id to it } }.toMap()
                    actions.run(null, { repo.saveAttendance(circle.id, dayIso, m) }) { n -> actions.say("حُفظ حضور ${ArabicText.students(n)}") }
                },
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(if (dirty) "حفظ الحضور" else if (isPending("att:${circle.id}:$dayIso")) "محفوظ على الجهاز • بانتظار الإرسال" else "محفوظ ✓") }
            Text("يصل إشعار لولي الأمر عند تسجيل الغياب.", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AttendanceHistory(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val kids = Permissions.visibleStudents(user, snap)
    var idx by remember { mutableIntStateOf(0) }
    val s = kids.getOrNull(idx)
    val rows = snap.attendance.filter { it.studentId == s?.id }.sortedByDescending { it.day }
    ScreenList("الحضور") {
        if (kids.size > 1) item { ChoiceChips(kids.map { it.fullName }, idx, { idx = it }) }
        if (s == null) item { EmptyState("لا يوجد طلاب", "") }
        else {
            item {
                AppCard {
                    Text("ملخص الحضور — ${s.fullName}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    DonutChart(
                        AttendanceStatus.entries.map { st -> Slice(st.label, rows.count { it.status == st }.toFloat(), attendanceColor(st)) },
                        centerText = "${rows.size} يوم"
                    )
                }
            }
            item { SectionHeader("السجل اليومي") }
            if (rows.isEmpty()) item { EmptyState("لا يوجد سجل حضور", "") }
            items(rows, key = { it.id }) { a ->
                AppCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val d = runCatching { LocalDate.parse(a.day) }.getOrNull()
                        Column(Modifier.weight(1f)) {
                            Text(d?.let { Fmt.dayTitle(it) } ?: a.day, style = MaterialTheme.typography.bodyMedium)
                            Text(snap.circle(a.circleId)?.name ?: "", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                        }
                        Pill(a.status.label, attendanceColor(a.status))
                    }
                }
            }
        }
    }
}
