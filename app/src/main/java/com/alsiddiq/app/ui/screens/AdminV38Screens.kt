@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alsiddiq.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.components.*
import com.alsiddiq.app.ui.theme.Brand
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/* v3.8 — his requests of Nov 8, 9:42–10:06 PM: «حضور وغياب المعلمين» and «الميزانية» (admin only), «الاشتراكات الشهرية»
   (a teacher for his own circle(s), the admin for all). Basic only; online (they need internet). */

internal object V38Fmt {
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))
    private val hijriMonths = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    fun month(m: YearMonth): String = monthFmt.format(m.atDay(1))
    /** The Hijri month(s) the Gregorian month spans, e.g. «جمادى الأولى – جمادى الآخرة ١٤٤٨هـ». */
    fun hijriMonth(m: YearMonth): String = try {
        val a = java.time.chrono.HijrahDate.from(m.atDay(1)); val b = java.time.chrono.HijrahDate.from(m.atEndOfMonth())
        val ma = hijriMonths[a.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1]; val mb = hijriMonths[b.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1]
        (if (ma == mb) ma else "$ma – $mb") + " " + b.get(java.time.temporal.ChronoField.YEAR) + "هـ"
    } catch (e: Exception) { "" }
    fun yer(x: Double): String = ArabicText.arabicThousands(Math.round(x).toInt())
    fun money(x: Double): String = yer(x) + " ريال يمني"
    fun digits(s: String): String = s.map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.filter { it.isDigit() || it == '.' }.joinToString("").take(10)
}

/** A month card with ‹ › (Gregorian month + Hijri month), like the attendance day card. */
@Composable
private fun MonthNavigator(month: YearMonth, onChange: (YearMonth) -> Unit) {
    AppCard(padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "الشهر السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(V38Fmt.month(month), style = MaterialTheme.typography.titleSmall)
                Text(V38Fmt.hijriMonth(month), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
            val canNext = month.isBefore(YearMonth.now())
            IconButton(onClick = { if (canNext) onChange(month.plusMonths(1)) }, enabled = canNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "الشهر التالي") }
        }
    }
}

@Composable
private fun TwoChoice(left: String, right: String, leftOn: Boolean?, leftColor: Color, rightColor: Color, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(left, leftOn == true, leftColor) to onLeft, Triple(right, leftOn == false, rightColor) to onRight).forEach { (t, click) ->
            val (label, sel, col) = t
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) col else col.copy(alpha = 0.08f)).clickable { click() },
                contentAlignment = Alignment.Center) { Text(label, color = if (sel) Color.White else col, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun LoadError(msg: String?, onRetry: () -> Unit) {
    if (msg != null) AppCard(padding = 12.dp) {
        Text(msg, style = MaterialTheme.typography.bodySmall, color = Brand.Red)
        TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
    }
}

// ============================ «حضور وغياب المعلمين» ============================
/** v3.9: حاضر / غائب / مستأذن, and «حضور و انصراف» (a time for the check-in and for the check-out, per teacher). Friday = rest. */
@Composable
private fun StatusChoice(selected: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(TeacherStatus.PRESENT to Brand.Green, TeacherStatus.ABSENT to Brand.Red, TeacherStatus.EXCUSED to Brand.Gold).forEach { (st, col) ->
            val sel = selected == st
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) col else col.copy(alpha = 0.08f)).clickable { onPick(st) },
                contentAlignment = Alignment.Center) { Text(TeacherStatus.label(st), color = if (sel) Color.White else col, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun RowScope.TimeChip(label: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    val col = Brand.Navy
    Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
        .background(if (value != null) col.copy(alpha = 0.14f) else col.copy(alpha = 0.05f))
        .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(if (value != null) "$label $value" else "$label —", color = if (enabled) col else Brand.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TimeDialog(title: String, initial: String?, onPick: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val parts = (initial ?: "08:00").split(":")
    val state = rememberTimePickerState(initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8, initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("تم") } },
        dismissButton = { Row { TextButton(onClick = onClear) { Text("مسح") }; TextButton(onClick = onDismiss) { Text("إلغاء") } } }
    )
}

@Composable
fun TeacherAttendanceScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val repo = LocalContainer.current.center
    val actions = rememberActions()
    if (user.role != Role.ADMIN) { ScreenList("حضور وغياب المعلمين", onBack = { nav.popBackStack() }) { item { EmptyState("للإدارة فقط", "") } }; return }
    val teachers = snap.members.filter { it.role == Role.TEACHER && it.active && !it.allCircles }.sortedBy { it.fullName }
    val today = LocalDate.now()
    // Friday is a rest day: the sheet opens on the last record day (Thursday on a Friday) and skips Friday.
    var day by remember { mutableStateOf(RestDay.lastWorkDayOnOrBefore(today)) }
    val month = YearMonth.from(day)
    var all by remember { mutableStateOf<List<TeacherMark>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(month, reload) {
        val from = minOf(month.atDay(1), LocalDate.now().minusDays(30)).toString()
        repo.teacherMarks(from, month.atEndOfMonth().toString()).onSuccess { all = it; error = null }.onFailure { error = it.message }
    }
    val dayIso = day.toString()
    val saved = all.filter { it.day == dayIso }.associateBy { it.teacherId }
    val marks = remember(dayIso, saved) { mutableStateMapOf<String, TeacherMark>().apply { putAll(saved) } }
    val dirty = teachers.any { marks[it.id] != saved[it.id] }
    val monthMarks = all.filter { it.day.startsWith(month.toString()) }
    var timeFor by remember { mutableStateOf<Pair<String, Boolean>?>(null) }  // (teacher id, true = check-in)
    val nextDay = RestDay.nextWorkDay(day, today)

    ScreenList("حضور وغياب المعلمين", onBack = { nav.popBackStack() }) {
        item {
            AppCard(padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { day = RestDay.prevWorkDay(day) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "اليوم السابق") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.dayTitle(day), style = MaterialTheme.typography.titleSmall)
                        Text(Fmt.hijri(day), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    IconButton(onClick = { nextDay?.let { day = it } }, enabled = nextDay != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "اليوم التالي")
                    }
                }
            }
        }
        if (RestDay.isRest(today)) item { AppCard(padding = 12.dp) { Text("اليوم الجمعة — يوم راحة", style = MaterialTheme.typography.titleSmall); Text(RestDay.MESSAGE, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) } }
        item { LoadError(error) { reload++ } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("حاضر ${teachers.count { marks[it.id]?.present == true }}", Brand.Green); Spacer(Modifier.width(6.dp))
                Pill("غائب ${teachers.count { marks[it.id]?.absent == true }}", Brand.Red); Spacer(Modifier.width(6.dp))
                Pill("مستأذن ${teachers.count { marks[it.id]?.excused == true }}", Brand.Gold); Spacer(Modifier.width(6.dp))
                Pill("لم يُحدَّد ${teachers.count { marks[it.id] == null }}", Brand.Muted)
            }
        }
        if (teachers.isEmpty()) item { EmptyState("لا يوجد معلمون", "أضف المعلمين من الإدارة ← المعلمون") }
        items(teachers, key = { it.id }) { t ->
            val m = marks[t.id]
            val hasStatus = m != null && m.status != TeacherStatus.ABSENT
            AppCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(t.fullName, 36.dp, Brand.Maroon)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(snap.circlesOf(t.id).joinToString("، ") { it.name }.ifBlank { "بدون حلقة" }, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatusChoice(m?.status) { st ->
                    val keep = st != TeacherStatus.ABSENT
                    marks[t.id] = TeacherMark(t.id, dayIso, st, if (keep) m?.checkIn else null, if (keep) m?.checkOut else null)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeChip("حضور", m?.checkIn, hasStatus) { timeFor = t.id to true }
                    TimeChip("انصراف", m?.checkOut, hasStatus) { timeFor = t.id to false }
                }
            }
        }
        if (teachers.isNotEmpty()) item {
            Button(onClick = {
                val list = teachers.mapNotNull { marks[it.id] }
                actions.run(null, { repo.saveTeacherMarks(dayIso, list) }) { n -> actions.say("حُفظ حضور $n من المعلمين"); reload++ }
            }, enabled = dirty, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (dirty) "حفظ" else "محفوظ ✓") }
        }
        item { SectionHeader("هذا الشهر — ${V38Fmt.month(month)}") }
        item {
            AppCard(padding = 12.dp) {
                if (teachers.isEmpty()) Text("—", color = Brand.Muted)
                teachers.forEach { t ->
                    val p = monthMarks.count { it.teacherId == t.id && it.present }
                    val a = monthMarks.count { it.teacherId == t.id && it.absent }
                    val e = monthMarks.count { it.teacherId == t.id && it.excused }
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(t.fullName, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("حاضر $p", Brand.Green); Spacer(Modifier.width(6.dp)); Pill("غائب $a", Brand.Red); Spacer(Modifier.width(6.dp)); Pill("مستأذن $e", Brand.Gold)
                        }
                    }
                }
            }
        }
        item { SectionHeader("السجل") }
        val days = all.map { it.day }.distinct().sortedDescending()
            .filter { d -> runCatching { !RestDay.isRest(LocalDate.parse(d)) }.getOrDefault(true) }.take(31)
        if (days.isEmpty()) item { EmptyState("لا يوجد سجل بعد", "يظهر هنا كل يوم يُسجَّل فيه الحضور") }
        items(days, key = { "d$it" }) { d ->
            val rows = all.filter { it.day == d }
            val ld = runCatching { LocalDate.parse(d) }.getOrNull()
            AppCard(padding = 12.dp, onClick = { if (ld != null) day = ld }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ld?.let { Fmt.dayTitle(it) } ?: d, style = MaterialTheme.typography.bodyMedium)
                        Text(ld?.let { Fmt.hijri(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    Pill("حاضر ${rows.count { it.present }}", Brand.Green); Spacer(Modifier.width(4.dp))
                    Pill("غائب ${rows.count { it.absent }}", Brand.Red); Spacer(Modifier.width(4.dp))
                    Pill("مستأذن ${rows.count { it.excused }}", Brand.Gold)
                }
            }
        }
    }

    timeFor?.let { (tid, isIn) ->
        val cur = marks[tid]
        TimeDialog(
            title = if (isIn) "وقت الحضور" else "وقت الانصراف",
            initial = if (isIn) cur?.checkIn else cur?.checkOut,
            onPick = { hhmm ->
                if (cur != null) marks[tid] = if (isIn) cur.copy(checkIn = hhmm) else cur.copy(checkOut = hhmm)
                timeFor = null
            },
            onClear = {
                if (cur != null) marks[tid] = if (isIn) cur.copy(checkIn = null) else cur.copy(checkOut = null)
                timeFor = null
            },
            onDismiss = { timeFor = null }
        )
    }
}

// ============================ «الميزانية» ============================
@Composable
fun BudgetScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val repo = LocalContainer.current.center
    val actions = rememberActions()
    if (user.role != Role.ADMIN) { ScreenList("الميزانية", onBack = { nav.popBackStack() }) { item { EmptyState("للإدارة فقط", "") } }; return }
    var entries by remember { mutableStateOf<List<BudgetEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { repo.budgetEntries().onSuccess { entries = it; error = null }.onFailure { error = it.message } }
    var monthOnly by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }       // 0 = الدخل, 1 = الصرف
    var editing by remember { mutableStateOf<BudgetEntry?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<BudgetEntry?>(null) }
    val thisMonth = YearMonth.now().toString()
    val shown = entries.filter { !monthOnly || it.day.startsWith(thisMonth) }
    val income = shown.filter { it.isIncome }.sumOf { it.amount }
    val expense = shown.filterNot { it.isIncome }.sumOf { it.amount }
    val left = income - expense
    val list = shown.filter { it.isIncome == (tab == 0) }

    ScreenList("الميزانية", onBack = { nav.popBackStack() }, fab = {
        AddFab(if (tab == 0) "دخل جديد" else "صرف جديد") {
            isNew = true
            editing = BudgetEntry(UUID.randomUUID().toString(), if (tab == 0) BudgetEntry.INCOME else BudgetEntry.EXPENSE, 0.0, LocalDate.now().toString())
        }
    }) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = Brand.Navy, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("للإدارة فقط: لا تظهر للمعلمين ولا للعرض العام", style = MaterialTheme.typography.bodySmall, color = Brand.Ink)
            }
        }
        item { ChoiceChips(listOf("الشهر", "الكل"), if (monthOnly) 0 else 1, { monthOnly = it == 0 }) }
        item { LoadError(error) { reload++ } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("الدخل", V38Fmt.yer(income), Icons.Outlined.SouthWest, Brand.Green, Modifier.weight(1f), sub = "ريال يمني")
                StatTile("الصرف", V38Fmt.yer(expense), Icons.Outlined.NorthEast, Brand.Red, Modifier.weight(1f), sub = "ريال يمني")
            }
        }
        item {
            AppCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = if (left < 0) Brand.Red else Brand.Gold)
                    Spacer(Modifier.width(10.dp))
                    Text("المتبقي", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text((if (left < 0) "−" else "") + V38Fmt.money(Math.abs(left)), style = MaterialTheme.typography.titleMedium, color = if (left < 0) Brand.Red else Brand.Navy, fontWeight = FontWeight.Bold)
                }
                Text(if (monthOnly) V38Fmt.month(YearMonth.now()) else "كل القيود", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
        }
        item { ChoiceChips(listOf("الدخل (${shown.count { it.isIncome }})", "الصرف (${shown.count { !it.isIncome }})"), tab, { tab = it }) }
        if (list.isEmpty()) item { EmptyState(if (tab == 0) "لا يوجد دخل" else "لا يوجد صرف", if (monthOnly) "في هذا الشهر" else "") }
        items(list, key = { it.id }) { e ->
            AppCard(padding = 12.dp, onClick = { isNew = false; editing = e }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val st = e.studentId?.let { snap.student(it) }
                    if (e.isIncome && st != null) StudentAvatar(st, 36.dp) else Avatar(if (e.isIncome) "د" else "ص", 36.dp, if (e.isIncome) Brand.Green else Brand.Red)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (e.isIncome) (st?.fullName ?: "دخل") else "صرف", style = MaterialTheme.typography.titleSmall)
                        Text(runCatching { Fmt.date(LocalDate.parse(e.day)) }.getOrDefault(e.day) + (if (e.isIncome) st?.let { s -> snap.circle(s.circleId)?.name?.let { " • $it" } } ?: "" else ""),
                            style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    Text((if (e.isIncome) "+" else "−") + V38Fmt.yer(e.amount), style = MaterialTheme.typography.titleSmall, color = if (e.isIncome) Brand.Green else Brand.Red, fontWeight = FontWeight.Bold)
                }
                if (e.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("ملاحظة: " + e.note, style = MaterialTheme.typography.bodySmall, color = Brand.Ink)
                }
            }
        }
    }
    editing?.let { e ->
        BudgetEntryDialog(e, isNew, snap, onDismiss = { editing = null }, onDelete = if (isNew) null else ({ editing = null; askDelete = e })) { out ->
            actions.run(if (isNew) "تمت الإضافة" else "تم الحفظ", { repo.saveBudgetEntry(out, isNew) }) { editing = null; reload++ }
        }
    }
    askDelete?.let { e ->
        ConfirmDialog("حذف القيد؟", (if (e.isIncome) "دخل " else "صرف ") + V38Fmt.money(e.amount), "حذف",
            { actions.run("حُذف", { repo.deleteBudgetEntry(e.id) }) { askDelete = null; reload++ } }, { askDelete = null }, destructive = true)
    }
}

@Composable
private fun BudgetEntryDialog(e: BudgetEntry, isNew: Boolean, snap: CenterSnapshot, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (BudgetEntry) -> Unit) {
    var studentId by remember { mutableStateOf(e.studentId) }
    var amount by remember { mutableStateOf(if (e.amount > 0) V38Fmt.digits(Math.round(e.amount).toString()) else "") }
    var day by remember { mutableStateOf(runCatching { LocalDate.parse(e.day) }.getOrDefault(LocalDate.now())) }
    var note by remember { mutableStateOf(e.note) }
    var pickStudent by remember { mutableStateOf(false) }
    val students = snap.students.filter { it.active }.sortedBy { it.fullName }
    val value = amount.toDoubleOrNull() ?: 0.0
    val ok = value > 0 && (e.isIncome || note.isNotBlank())
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (e.isIncome) (if (isNew) "دخل جديد (اشتراك طالب)" else "تعديل الدخل") else (if (isNew) "صرف جديد" else "تعديل الصرف")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (e.isIncome) SelectorField("الطالب", students.firstOrNull { it.id == studentId }?.fullName ?: "اختر الطالب") { pickStudent = true }
                Field(amount, { amount = V38Fmt.digits(it) }, "المبلغ (ريال يمني)", keyboard = KeyboardType.Number)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "اليوم السابق") }
                    Text(Fmt.date(day), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) }, enabled = day.isBefore(LocalDate.now())) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "اليوم التالي") }
                }
                Field(note, { note = it.take(500) }, if (e.isIncome) "ملاحظة (اختياري)" else "ملاحظة الصرف (مطلوبة)", singleLine = false, minLines = 2)
                if (!e.isIncome && note.isBlank()) Text("اكتب ملاحظة: على ماذا صُرف المبلغ", style = MaterialTheme.typography.labelSmall, color = Brand.Red)
                if (onDelete != null) TextButton(onClick = onDelete) { Text("حذف القيد", color = Brand.Red) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(e.copy(studentId = if (e.isIncome) studentId else null, amount = value, day = day.toString(), note = note.trim())) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
    if (pickStudent) PickerDialog("اختر الطالب", students, { s -> s.fullName + (snap.circle(s.circleId)?.name?.let { " — $it" } ?: "") }, { s ->
        studentId = s.id
        // prefill from the student's circle fee (v3.5)
        if (amount.isBlank()) snap.circle(s.circleId)?.feeYer?.takeIf { it > 0 }?.let { amount = it.toString() }
    }, { pickStudent = false }, searchable = true)
}

// ============================ «الاشتراكات الشهرية» ============================
@Composable
fun SubscriptionsScreen(user: Member, nav: NavHostController, circleId: String?) {
    val snap = snapshot()
    val repo = LocalContainer.current.center
    val actions = rememberActions()
    val circles = Permissions.ownCircles(user, snap)
    if (user.role == Role.VIEWER || circles.isEmpty()) {
        ScreenList("الاشتراكات الشهرية", onBack = { nav.popBackStack() }) { item { EmptyState("لا توجد حلقة لك", "يظهر كشف الاشتراكات لمعلم الحلقة وللإدارة") } }; return
    }
    var circleIdx by remember { mutableIntStateOf(circles.indexOfFirst { it.id == circleId }.coerceAtLeast(0)) }
    val circle = circles.getOrNull(circleIdx) ?: circles.first()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val monthIso = month.atDay(1).toString()
    var savedList by remember { mutableStateOf<List<SubscriptionMark>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(monthIso, reload) { repo.subscriptions(monthIso).onSuccess { savedList = it; error = null }.onFailure { error = it.message } }
    val students = snap.studentsIn(circle.id).sortedBy { it.fullName }
    val saved = savedList.associateBy { it.studentId }
    val fee = circle.feeYer?.takeIf { it > 0 }
    val paid = remember(monthIso, circle.id, savedList) { mutableStateMapOf<String, Boolean>().apply { students.forEach { s -> saved[s.id]?.let { put(s.id, it.paid) } } } }
    val amounts = remember(monthIso, circle.id, savedList) { mutableStateMapOf<String, String>().apply {
        students.forEach { s -> put(s.id, saved[s.id]?.amount?.let { V38Fmt.digits(Math.round(it).toString()) } ?: fee?.toString() ?: "") } } }
    fun changed(s: Student): Boolean {
        val p = paid[s.id] ?: return false
        val old = saved[s.id]
        return old == null || old.paid != p || (p && (amounts[s.id]?.toDoubleOrNull() ?: 0.0) != (old.amount ?: 0.0))
    }
    val dirty = students.any { changed(it) }
    val nPaid = students.count { paid[it.id] == true }
    val total = students.filter { paid[it.id] == true }.sumOf { amounts[it.id]?.toDoubleOrNull() ?: 0.0 }

    ScreenList("الاشتراكات الشهرية", onBack = { nav.popBackStack() }) {
        item { ChoiceChips(circles.map { it.name }, circleIdx, { circleIdx = it }) }
        item { MonthNavigator(month) { month = it } }
        item { LoadError(error) { reload++ } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("سلم $nPaid", Brand.Green); Spacer(Modifier.width(6.dp))
                Pill("لم يسلم ${students.size - nPaid}", Brand.Red); Spacer(Modifier.width(6.dp))
                Pill("المجموع ${V38Fmt.yer(total)} ر.ي", Brand.Gold)
            }
        }
        if (fee != null) item { Text("رسوم الحلقة: ${ArabicText.fee(fee, circle.feePeriod)?.removePrefix("الرسوم: ")}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted) }
        if (students.isEmpty()) item { EmptyState("لا يوجد طلاب", "اختر حلقة فيها طلاب") }
        items(students, key = { it.id }) { s ->
            AppCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(s, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.fullName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                TwoChoice("سلم", "لم يسلم", paid[s.id], Brand.Green, Brand.Red, { paid[s.id] = true }, { paid[s.id] = false })
                if (paid[s.id] == true) {
                    Spacer(Modifier.height(8.dp))
                    Field(amounts[s.id] ?: "", { amounts[s.id] = V38Fmt.digits(it) }, "المبلغ المسلَّم (ريال يمني)", keyboard = KeyboardType.Number)
                }
            }
        }
        if (students.isNotEmpty()) item {
            Button(onClick = {
                val todo = students.filter { changed(it) }.map { s -> SubscriptionMark(s.id, monthIso, paid[s.id] == true, if (paid[s.id] == true) amounts[s.id]?.toDoubleOrNull() else null) }
                actions.run(null, { runCatching { todo.forEach { repo.saveSubscription(it).getOrThrow() }; todo.size } }) { n -> actions.say("حُفظ اشتراك ${ArabicText.students(n)}"); reload++ }
            }, enabled = dirty, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (dirty) "حفظ" else "محفوظ ✓") }
        }
    }
}


// ============================ «وسام الشرف لهذا الأسبوع» (automatic) ============================
/** One row per circle: «وسام الشرف لهذا الأسبوع — من حلقة الأستاذ عصام: الطالب أحمد». Seen by everyone (viewer included). */
@Composable
fun WeeklyStarCard(snap: CenterSnapshot, modifier: Modifier = Modifier) {
    val circles = snap.circles.filter { it.active }.sortedBy { it.name }
    if (circles.isEmpty()) return
    val today = LocalDate.now()
    val stars = remember(snap, today) { circles.map { it to Stats.weeklyStar(snap, it.id, today) } }
    AppCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
                ArtOrIcon("وسام الشرف لهذا الأسبوع", Icons.Outlined.MilitaryTech, Brand.Gold, 34.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("وسام الشرف لهذا الأسبوع", style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
                Text("نجم كل حلقة: الأكثر حفظاً وتسميعاً منذ السبت", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
        }
        stars.forEach { (c, s) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s != null) StudentAvatar(s, 36.dp) else Avatar("؟", 36.dp, Brand.Muted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val t = snap.teacherOf(c)
                    val from = when {
                        t.isNullOrBlank() -> "من ${c.name}"
                        t.startsWith("الأستاذ") || t.startsWith("الاستاذ") || t.startsWith("الشيخ") -> "من حلقة $t"
                        else -> "من حلقة الأستاذ $t"
                    }
                    Text(from, style = MaterialTheme.typography.labelSmall, color = Brand.Muted, maxLines = 2)
                    Text(if (s != null) "الطالب ${s.fullName}" else "لم يُحدد بعد", style = MaterialTheme.typography.bodyMedium,
                        color = if (s != null) Brand.Ink else Brand.Muted, fontWeight = if (s != null) FontWeight.SemiBold else FontWeight.Normal)
                }
                if (s != null) Icon(Icons.Outlined.MilitaryTech, null, tint = Brand.Gold, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** v3.8: «نجم الحلقة لهذا الأسبوع» inside each circle (the circle card and the top of the circle page). Automatic, as on the home card. */
@Composable
fun CircleStarRow(snap: CenterSnapshot, c: Circle, big: Boolean = false) {
    val today = LocalDate.now()
    val s = remember(snap, c.id, today) { Stats.weeklyStar(snap, c.id, today) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft).padding(horizontal = 10.dp, vertical = if (big) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.MilitaryTech, null, tint = Brand.Gold, modifier = Modifier.size(if (big) 26.dp else 20.dp))
        Spacer(Modifier.width(6.dp))
        Text("نجم الحلقة لهذا الأسبوع", style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium, color = Brand.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (s != null) {
            StudentAvatar(s, if (big) 34.dp else 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(s.fullName, style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge, color = Brand.Navy, fontWeight = FontWeight.SemiBold, maxLines = 1)
        } else Text("لم يُحدد بعد", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
    }
}
