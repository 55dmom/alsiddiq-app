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
import com.alsiddiq.app.data.NewMember
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.components.*
import com.alsiddiq.app.ui.theme.Brand
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val visible = Permissions.visibleStudents(user, snap)
    val circles = Permissions.visibleCircles(user, snap)
    // Scope: whole center / one circle (staff) or one child (parent)
    val scopeOptions = if (user.role == Role.PARENT) visible.map { it.fullName } else listOf("المركز كاملاً") + circles.map { it.name }
    var scopeIdx by remember { mutableIntStateOf(0) }
    var rangeIdx by remember { mutableIntStateOf(1) }
    val ranges = listOf(7L to "أسبوع", 30L to "شهر", 90L to "3 أشهر")
    val students = when {
        user.role == Role.PARENT -> listOfNotNull(visible.getOrNull(scopeIdx))
        scopeIdx == 0 -> visible
        else -> visible.filter { it.circleId == circles.getOrNull(scopeIdx - 1)?.id }
    }
    val since = LocalDate.now().minusDays(ranges[rangeIdx].first - 1)
    val ids = students.map { it.id }.toSet()
    val recs = snap.recitations.filter { it.studentId in ids && !Stats.dayOf(it.recordedAt).isBefore(since) }
    val att = snap.attendance.filter { it.studentId in ids && !LocalDate.parse(it.day).isBefore(since) }
    val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
    val counted = att.count { it.status != AttendanceStatus.EXCUSED }
    val hifz = recs.filter { it.kind == RecordKind.HIFZ }.sumOf { Stats.ayatOf(it) }
    val muraja = recs.filter { it.kind != RecordKind.HIFZ }.sumOf { Stats.ayatOf(it) }
    val pages = recs.filter { it.kind == RecordKind.HIFZ }.sumOf { Stats.pagesOf(it) }
    val days = ranges[rangeIdx].first.toInt().coerceAtMost(30)
    val series = (days - 1 downTo 0).map { back ->
        val d = LocalDate.now().minusDays(back.toLong())
        d to recs.filter { Stats.dayOf(it.recordedAt) == d }.sumOf { Stats.ayatOf(it) }
    }

    ScreenList("التقارير") {
        item {
            ChoiceChips(scopeOptions, scopeIdx, { scopeIdx = it })
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ranges.forEachIndexed { i, (_, label) ->
                    SegmentedButton(selected = rangeIdx == i, onClick = { rangeIdx = i }, shape = SegmentedButtonDefaults.itemShape(i, ranges.size)) { Text(label) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("آيات حفظ جديد", "$hifz", Icons.Outlined.AutoStories, Brand.Navy, Modifier.weight(1f), sub = "${Fmt.one(pages)} صفحة")
                StatTile("آيات مراجعة وتسميع", "$muraja", Icons.Outlined.Replay, Brand.Maroon, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("نسبة الحضور", if (counted > 0) Fmt.pct(present.toFloat() / counted) else "—", Icons.Outlined.EventAvailable, Brand.Green, Modifier.weight(1f))
                StatTile("متوسط التقدير", if (recs.isNotEmpty()) Fmt.one(recs.map { it.grade.score }.average()) + "/5" else "—", Icons.Outlined.StarOutline, Brand.Gold, Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                Text("الآيات يومياً", style = MaterialTheme.typography.titleSmall)
                Text("اضغط على أي عمود لرؤية قيمته", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(10.dp))
                BarChart(series.map { (d, n) -> BarDatum(shortDayLabel(d) + " " + d.dayOfMonth, n.toFloat(), "$n آية") })
            }
        }
        item {
            AppCard {
                Text("توزيع التقديرات", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                DonutChart(Grade.entries.map { g -> Slice(g.label, recs.count { it.grade == g }.toFloat(), gradeColor(g)) }, centerText = "${recs.size}")
            }
        }
        if (user.role != Role.PARENT) {
            item { SectionHeader("ترتيب الطلاب حسب الحفظ") }
            val ranked = students.map { s -> s to recs.filter { it.studentId == s.id && it.kind == RecordKind.HIFZ }.sumOf { Stats.ayatOf(it) } }
                .sortedByDescending { it.second }
            val max = (ranked.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            items(ranked, key = { it.first.id }) { (s, n) ->
                AppCard(onClick = { nav.navigate("student/${s.id}") }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.fullName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$n آية", style = MaterialTheme.typography.labelLarge, color = Brand.Navy)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearMeter(n.toFloat() / max, Brand.Navy)
                }
            }
        } else {
            val s = students.firstOrNull()
            if (s != null) item {
                Button(onClick = { nav.navigate("student/${s.id}") }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("ملف ${s.fullName} الكامل")
                }
            }
        }
    }
}
