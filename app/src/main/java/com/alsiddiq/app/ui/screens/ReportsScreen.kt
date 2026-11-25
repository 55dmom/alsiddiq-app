@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.components.*
import com.alsiddiq.app.ui.theme.Brand
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reportsMonthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))

/**
 * Build 17: Open read-only reports, past-days records, attendance, and 500-point monthly grades
 * for everyone including العامة (Role.VIEWER), with zero edit/add/delete controls.
 */
@Composable
fun ReportsScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val visible = Permissions.visibleStudents(user, snap)
    val circles = Permissions.visibleCircles(user, snap)
    val scopeOptions = listOf("المركز كاملاً") + circles.map { it.name }
    var scopeIdx by remember { mutableIntStateOf(0) }
    var sectionIdx by remember { mutableIntStateOf(0) }
    val sections = listOf("سجل الأيام", "الدرجات", "ملخص الفترة")

    val students = when (scopeIdx) {
        0 -> visible
        else -> visible.filter { it.circleId == circles.getOrNull(scopeIdx - 1)?.id }
    }
    val ids = remember(students) { students.map { it.id }.toSet() }
    val today = LocalDate.now()

    // All distinct past days that have attendance or recitation records in this scope (newest first).
    val recordedDays = remember(snap, ids) {
        val d1 = snap.recitations.filter { it.studentId in ids }.map { Stats.dayOf(it.recordedAt) }
        val d2 = snap.attendance.filter { it.studentId in ids }.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        (d1 + d2).distinct().sortedDescending()
    }
    var selectedDay by remember(recordedDays.firstOrNull()) {
        mutableStateOf(recordedDays.firstOrNull() ?: today)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // Month state for the 500-point monthly grades tab (all past months supported).
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }

    // Period overview state (includes «الكل» for all past history).
    var rangeIdx by remember { mutableIntStateOf(1) }
    val ranges = listOf(7L to "أسبوع", 30L to "شهر", 90L to "3 أشهر", 0L to "الكل")

    ScreenList("التقارير") {
        if (user.role == Role.VIEWER) {
            item {
                ReadOnlyNote("للعامة وأولياء الأمور: جميع التقارير والسجلات والحضور والدرجات لكل الأيام السابقة (للعرض فقط).")
            }
        }
        item {
            ChoiceChips(scopeOptions, scopeIdx, { scopeIdx = it })
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sections.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = sectionIdx == i,
                        onClick = { sectionIdx = i },
                        shape = SegmentedButtonDefaults.itemShape(i, sections.size)
                    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        when (sectionIdx) {
            // 1) سجل الأيام: browse ANY past day (attendance + recitations + reviews + errors)
            0 -> {
                item {
                    AppCard(padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { selectedDay = selectedDay.minusDays(1) }) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "اليوم السابق", tint = Brand.Navy)
                            }
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showDatePicker = true }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.CalendarMonth, null, tint = Brand.Navy, modifier = Modifier.size(18.dp))
                                    Text(Fmt.dayTitle(selectedDay), style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
                                }
                                Text(Fmt.date(selectedDay) + " • اضغط لاختيار أي يوم سابق", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                            }
                            val canNext = selectedDay.isBefore(today)
                            IconButton(onClick = { if (canNext) selectedDay = selectedDay.plusDays(1) }, enabled = canNext) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "اليوم التالي", tint = if (canNext) Brand.Navy else Brand.Muted.copy(alpha = 0.35f))
                            }
                        }
                        if (recordedDays.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                recordedDays.take(20).forEach { d ->
                                    FilterChip(
                                        selected = d == selectedDay,
                                        onClick = { selectedDay = d },
                                        label = { Text(if (d == today) "اليوم" else if (d == today.minusDays(1)) "أمس" else "${d.dayOfMonth}/${d.monthValue}") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Brand.Navy, selectedLabelColor = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                val dayIso = selectedDay.toString()
                val dayAtt = snap.attendance.filter { it.studentId in ids && it.day == dayIso }
                    .sortedBy { snap.student(it.studentId)?.fullName ?: "" }
                val dayRecs = snap.recitations.filter { it.studentId in ids && Stats.dayOf(it.recordedAt) == selectedDay }
                    .sortedByDescending { it.recordedAt }
                val presentN = dayAtt.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                val excusedN = dayAtt.count { it.status == AttendanceStatus.EXCUSED }
                val absentN = dayAtt.count { it.status == AttendanceStatus.ABSENT }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("حاضر", "$presentN", Icons.Outlined.CheckCircle, Brand.Green, Modifier.weight(1f))
                        StatTile("مستأذن", "$excusedN", Icons.Outlined.EventNote, Brand.Gold, Modifier.weight(1f))
                        StatTile("غائب", "$absentN", Icons.Outlined.Cancel, Brand.Red, Modifier.weight(1f))
                    }
                }

                item { SectionHeader("سجلات التسميع والحفظ (${dayRecs.size})") }
                if (dayRecs.isEmpty()) {
                    item { EmptyState("لا توجد سجلات في هذا اليوم", "اختر يوماً سابقاً من الشريط أعلاه أو التقويم") }
                } else {
                    items(dayRecs, key = { "dr_${it.id}" }) { r ->
                        RecordRow(r, snap, showStudent = true, onDelete = null)
                    }
                }

                item { SectionHeader("كشف الحضور (${dayAtt.size})") }
                if (dayAtt.isEmpty()) {
                    item { EmptyState("لم يُسجَّل حضور في هذا اليوم", "") }
                } else {
                    items(dayAtt, key = { "da_${it.id}" }) { a ->
                        val st = snap.student(a.studentId)
                        val stColor = when (a.status) {
                            AttendanceStatus.PRESENT, AttendanceStatus.LATE -> Brand.Green
                            AttendanceStatus.EXCUSED -> Brand.Gold
                            AttendanceStatus.ABSENT -> Brand.Red
                        }
                        AppCard(
                            onClick = { st?.let { nav.navigate("student/${it.id}") } },
                            padding = 12.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(st?.fullName ?: "طالب", style = MaterialTheme.typography.titleSmall)
                                    Text(snap.circle(a.circleId)?.name ?: "", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                                }
                                Pill(a.status.label, stColor, filled = true)
                            }
                        }
                    }
                }
            }

            // 2) الدرجات الشهرية (من 500) — all past months
            1 -> {
                item {
                    AppCard(padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "الشهر السابق", tint = Brand.Navy)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(reportsMonthFmt.format(selectedMonth.atDay(1)), style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
                                Text("الدرجات من 500 (حفظ وسرد 350 • مراجعة 100 • حضور 50)", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                            }
                            val canNextMonth = selectedMonth.isBefore(YearMonth.now())
                            IconButton(onClick = { if (canNextMonth) selectedMonth = selectedMonth.plusMonths(1) }, enabled = canNextMonth) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "الشهر التالي", tint = if (canNextMonth) Brand.Navy else Brand.Muted.copy(alpha = 0.35f))
                            }
                        }
                    }
                }

                val scored = students.map { s -> s to Stats.monthlyScore(s, snap, selectedMonth) }
                    .sortedWith(compareByDescending<Pair<Student, MonthlyScore>> { it.second.totalScore }.thenBy { it.first.fullName })

                if (scored.isEmpty()) {
                    item { EmptyState("لا يوجد طلاب", "") }
                } else {
                    val avgScore = scored.map { it.second.totalScore }.average()
                    val avgPct = ((avgScore / Stats.MAX_TOTAL_SCORE) * 100.0).toInt().coerceIn(0, 100)
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("متوسط الدرجات", "${avgScore.toInt()}/500", Icons.Outlined.EmojiEvents, Brand.Maroon, Modifier.weight(1f), sub = "$avgPct%")
                            StatTile("عدد الطلاب", "${scored.size}", Icons.Outlined.School, Brand.Navy, Modifier.weight(1f))
                        }
                    }
                    items(scored, key = { "sc_${it.first.id}" }) { (s, ms) ->
                        val rank = scored.indexOfFirst { it.first.id == s.id } + 1
                        AppCard(onClick = { nav.navigate("student/${s.id}") }, padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$rank.", style = MaterialTheme.typography.titleSmall, color = Brand.Navy, modifier = Modifier.width(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${snap.circle(s.circleId)?.name ?: "بدون حلقة"} • الهدف: ${Fmt.one(ms.monthlyJuzTarget)} جزء",
                                        style = MaterialTheme.typography.labelSmall, color = Brand.Muted
                                    )
                                }
                                Pill(ms.summaryText(), Brand.Maroon, filled = true)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearMeter((ms.totalScore / Stats.MAX_TOTAL_SCORE).toFloat().coerceIn(0f, 1f), Brand.Maroon)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("حفظ وسرد: ${ms.hifzRounded}/350", style = MaterialTheme.typography.labelSmall, color = Brand.Navy)
                                Text("مراجعة: ${ms.reviewRounded}/100", style = MaterialTheme.typography.labelSmall, color = Brand.Maroon)
                                Text("حضور: ${ms.attendanceRounded}/50", style = MaterialTheme.typography.labelSmall, color = Brand.Green)
                            }
                        }
                    }
                }
            }

            // 3) ملخص الفترة: charts & overall stats (including «الكل» for all past days)
            else -> {
                val rangeDays = ranges[rangeIdx].first
                val since = if (rangeDays == 0L) null else today.minusDays(rangeDays - 1)
                val recs = snap.recitations.filter { it.studentId in ids && (since == null || !Stats.dayOf(it.recordedAt).isBefore(since)) }
                val att = snap.attendance.filter { a ->
                    a.studentId in ids && (since == null || runCatching { !LocalDate.parse(a.day).isBefore(since) }.getOrDefault(true))
                }
                val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                val counted = att.count { it.status != AttendanceStatus.EXCUSED }
                val hifz = recs.filter { it.kind == RecordKind.HIFZ || it.kind == RecordKind.TASMEE || it.kind == RecordKind.SARD }.sumOf { Stats.ayatOf(it) }
                val muraja = recs.filter { it.kind == RecordKind.MURAJA }.sumOf { Stats.ayatOf(it) } + recs.sumOf { Stats.reviewAyatOf(it) }
                val pages = recs.filter { it.kind == RecordKind.HIFZ || it.kind == RecordKind.TASMEE || it.kind == RecordKind.SARD }.sumOf { Stats.pagesOf(it) }
                val chartDays = if (rangeDays == 0L) 30 else rangeDays.toInt().coerceAtMost(30)
                val series = (chartDays - 1 downTo 0).map { back ->
                    val d = today.minusDays(back.toLong())
                    d to recs.filter { Stats.dayOf(it.recordedAt) == d }.sumOf { Stats.ayatOf(it) }
                }

                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ranges.forEachIndexed { i, (_, label) ->
                            SegmentedButton(selected = rangeIdx == i, onClick = { rangeIdx = i }, shape = SegmentedButtonDefaults.itemShape(i, ranges.size)) { Text(label) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("حفظ وتسميع وسرد", "$hifz", Icons.Outlined.AutoStories, Brand.Navy, Modifier.weight(1f), sub = "${Fmt.one(pages)} صفحة")
                        StatTile("آيات المراجعة", "$muraja", Icons.Outlined.Replay, Brand.Maroon, Modifier.weight(1f))
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
                        DonutChart(Grade.entries.map { g -> Slice(if (g == Grade.WEAK) "إعادة" else g.label, recs.count { it.grade == g }.toFloat(), gradeColor(g)) }, centerText = "${recs.size}")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        selectedDay = if (picked.isAfter(today)) today else picked
                    }
                    showDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
