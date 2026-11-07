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
fun TeacherHome(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val demo = isDemo()
    val circles = Permissions.visibleCircles(user, snap)
    val students = Permissions.visibleStudents(user, snap)
    val stats = remember(snap) { Stats.forCenter(snap, students) }
    val today = LocalDate.now()
    val logged = snap.recitations.filter { Stats.dayOf(it.recordedAt) == today }.map { it.studentId }.toSet()
    val pending = students.filter { it.id !in logged }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HomeHeader("السلام عليكم", user.fullName, Fmt.dayTitle(today) + " • " + Fmt.hijri(today), snap.notifications.count { !it.read }) { nav.navigate("notifications") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatusBanners(Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(if (students.isEmpty()) 0f else logged.count { id -> students.any { it.id == id } }.toFloat() / students.size, size = 92.dp, stroke = 10.dp, color = Brand.Green, track = Brand.GreenSoft) {
                            Text("${students.count { it.id in logged }}/${students.size}", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تسميع اليوم", style = MaterialTheme.typography.titleSmall)
                            Text(if (pending.isEmpty()) "أحسنت! سُجّل جميع الطلاب" else "بقي ${pending.size} طالب لم يُسجَّل لهم", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { nav.navigate("record_start") }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("تسجيل الآن")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("طلابي", "${students.size}", Icons.Outlined.School, Brand.Navy, Modifier.weight(1f))
                    StatTile("آيات الأسبوع", "${stats.ayatThisWeek}", Icons.Outlined.AutoStories, Brand.Maroon, Modifier.weight(1f))
                    StatTile("حضور اليوم", if (stats.attendanceToday > 0) Fmt.pct(stats.attendanceToday) else "—", Icons.Outlined.FactCheck, Brand.Green, Modifier.weight(1f), onClick = { nav.navigate("attendance") })
                }
                SectionHeader("حلقاتي")
            }
        }
        items(circles) { c -> Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { CircleCard(c, snap) { nav.navigate("circle/${c.id}") } } }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("أدوات", null)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("المكافآت", Icons.Outlined.EmojiEvents, Modifier.weight(1f)) { nav.navigate("rewards") }
                    QuickAction("التقارير", Icons.Outlined.Insights, Modifier.weight(1f)) { nav.navigate("reports") }
                    QuickAction("الإعلانات", Icons.Outlined.Campaign, Modifier.weight(1f)) { nav.navigate("announcements") }
                }
                SectionHeader("آخر ما سجّلت")
            }
        }
        val recent = snap.recitations.filter { it.teacherId == user.id }.sortedByDescending { it.recordedAt }.take(6)
        items(recent, key = { it.id }) { r -> Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { RecordRow(r, snap, showStudent = true) } }
    }
}

@Composable
fun StudentHome(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val demo = isDemo()
    val s = snap.student(user.studentId)
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HomeHeader("السلام عليكم", user.fullName, "«خيركم من تعلّم القرآن وعلّمه»", snap.notifications.count { !it.read }) { nav.navigate("notifications") }
        }
        item { Box(Modifier.padding(horizontal = 16.dp)) { StatusBanners() } }
        if (s == null) item { EmptyState("الحساب غير مرتبط بسجل طالب", "تواصل مع إدارة المركز") }
        else {
            // Same dashboard the teacher and parent see, with side padding.
            studentDashboard(this, s, snap, nav, user, hPad = 16.dp)
        }
    }
}

@Composable
fun ParentHome(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val demo = isDemo()
    val kids = Permissions.visibleStudents(user, snap)
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HomeHeader("مرحباً بك", user.fullName, "تابع حفظ أبنائك وحضورهم يوماً بيوم", snap.notifications.count { !it.read }) { nav.navigate("notifications") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatusBanners(Modifier.padding(top = 12.dp))
                SectionHeader("أبنائي")
                if (kids.isEmpty()) EmptyState("لا يوجد أبناء مرتبطون", "تواصل مع إدارة المركز لربط حسابك بأبنائك")
            }
        }
        items(kids, key = { it.id }) { s ->
            val st = remember(s, snap) { Stats.forStudent(s, snap) }
            val todayAtt = snap.attendance.firstOrNull { it.studentId == s.id && it.day == today.toString() }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                AppCard(onClick = { nav.navigate("student/${s.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(st.goalProgress, size = 84.dp, stroke = 9.dp) {
                            Text(Fmt.pct(st.goalProgress), style = MaterialTheme.typography.labelLarge, color = Brand.Navy)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(snap.circle(s.circleId)?.name ?: "", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            Spacer(Modifier.height(6.dp))
                            Text("${Fmt.one(st.juzEquivalent)} جزء من ${s.goalJuz} • ${st.points} نقطة", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                if (todayAtt != null) { Pill("اليوم: ${todayAtt.status.label}", attendanceColor(todayAtt.status)); Spacer(Modifier.width(6.dp)) }
                                st.lastRecord?.let { Pill(it.grade.label, gradeColor(it.grade)) }
                            }
                        }
                    }
                    st.lastRecord?.let {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Brand.Line)
                        Spacer(Modifier.height(8.dp))
                        Text("آخر تسميع: ${it.kind.label} — ${Stats.describeRange(it)} • ${Fmt.relative(it.recordedAt)}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("إعلانات المركز", "الكل") { nav.navigate("announcements") }
                snap.announcements.sortedByDescending { it.createdAt }.take(2).forEach { a ->
                    AppCard(onClick = { nav.navigate("announcements") }, padding = 12.dp) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.body, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

data class AwardBadge(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val earned: Boolean)

fun badgesFor(st: StudentStats): List<AwardBadge> = listOf(
    AwardBadge("أول جزء", Icons.Outlined.MenuBook, st.juzEquivalent >= 1),
    AwardBadge("100 آية", Icons.Outlined.AutoStories, st.memorisedAyat >= 100),
    AwardBadge("أسبوع متواصل", Icons.Outlined.LocalFireDepartment, st.streakDays >= 6),
    AwardBadge("حضور 90%", Icons.Outlined.EventAvailable, st.attendanceRate >= 0.9f && st.presentDays >= 10),
    AwardBadge("متقن", Icons.Outlined.WorkspacePremium, st.averageGrade >= 4.5),
    AwardBadge("5 أجزاء", Icons.Outlined.Diamond, st.juzEquivalent >= 5)
)

@Composable
fun RewardsScreen(user: Member, nav: NavHostController, onBack: (() -> Unit)?) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val visible = Permissions.visibleStudents(user, snap)
    val canGive = user.role == Role.ADMIN || user.role == Role.TEACHER
    var giving by remember { mutableStateOf<Student?>(null) }
    var pickStudent by remember { mutableStateOf(false) }
    ScreenList("المكافآت", onBack = onBack, fab = { if (canGive) AddFab("منح مكافأة") { pickStudent = true } }) {
        if (user.role == Role.STUDENT || user.role == Role.PARENT) {
            visible.forEach { s ->
                item(key = "card" + s.id) {
                    val st = remember(s, snap) { Stats.forStudent(s, snap) }
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = Brand.Gold, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.fullName, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                                Text("${st.points} نقطة", style = MaterialTheme.typography.headlineSmall, color = Brand.Ink)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("الأوسمة", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        val badges = badgesFor(st)
                        badges.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { b ->
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                            .background(if (b.earned) Brand.NavySoft else Brand.Bg).padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(b.icon, null, tint = if (b.earned) Brand.Navy else Brand.Line, modifier = Modifier.size(28.dp))
                                        Text(b.title, style = MaterialTheme.typography.labelSmall, color = if (b.earned) Brand.Navy else Brand.Muted, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            item { SectionHeader("سجل النقاط") }
        } else {
            item { SectionHeader("لوحة الشرف") }
            val board = visible.map { s -> s to snap.rewards.filter { it.studentId == s.id }.sumOf { it.points } }.sortedByDescending { it.second }.take(10)
            items(board, key = { "b" + it.first.id }) { (s, pts) ->
                val rank = board.indexOfFirst { it.first.id == s.id } + 1
                AppCard(padding = 12.dp, onClick = { giving = s }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(when (rank) { 1 -> Brand.Gold; 2 -> Color(0xFFA7A9B4); 3 -> Color(0xFFB0773F); else -> Brand.NavySoft }),
                            contentAlignment = Alignment.Center
                        ) { Text("$rank", color = if (rank <= 3) Color.White else Brand.Navy, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Text(s.fullName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("$pts", style = MaterialTheme.typography.titleMedium, color = Brand.Maroon)
                        Spacer(Modifier.width(4.dp))
                        Text("نقطة", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                }
            }
            item { SectionHeader("أحدث المكافآت") }
        }
        val ids = visible.map { it.id }.toSet()
        val history = snap.rewards.filter { it.studentId in ids }.sortedByDescending { it.createdAt }.take(30)
        if (history.isEmpty()) item { EmptyState("لا توجد مكافآت بعد", "") }
        items(history, key = { it.id }) { r ->
            AppCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("+${r.points}", Brand.Green, filled = true)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                        Text("${snap.student(r.studentId)?.fullName ?: ""} • ${Fmt.relative(r.createdAt)}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                }
            }
        }
    }
    if (pickStudent) PickerDialog("اختر الطالب", visible.filter { Permissions.canRecordFor(user, it, snap) }, { it.fullName }, { giving = it }, { pickStudent = false }, searchable = true)
    giving?.let { s ->
        if (!canGive) { giving = null; return@let }
        var pts by remember(s.id) { mutableIntStateOf(10) }
        var reason by remember(s.id) { mutableStateOf("إتقان الحفظ") }
        AlertDialog(
            onDismissRequest = { giving = null },
            title = { Text("مكافأة لـ ${s.fullName}") },
            text = {
                Column {
                    Text("النقاط", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    val opts = listOf(5, 10, 15, 20, 50)
                    ChoiceChips(opts.map { "+$it" }, opts.indexOf(pts), { pts = opts[it] })
                    Spacer(Modifier.height(10.dp))
                    val reasons = listOf("إتقان الحفظ", "الانضباط في الحضور", "حسن الخلق", "ختم سورة", "التفوق في المسابقة")
                    ChoiceChips(reasons, reasons.indexOf(reason), { reason = reasons[it] })
                    Spacer(Modifier.height(10.dp))
                    Field(reason, { reason = it }, "السبب")
                }
            },
            confirmButton = {
                Button(onClick = {
                    actions.run("مُنحت المكافأة 🎉", { repo.giveReward(Reward("", s.id, pts, reason.trim(), user.id)) }) { giving = null }
                }) { Text("منح") }
            },
            dismissButton = { TextButton(onClick = { giving = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ProfileScreen(user: Member, nav: NavHostController, onBack: (() -> Unit)?) {
    val container = LocalContainer.current
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    var changing by remember { mutableStateOf(false) }
    var askLogout by remember { mutableStateOf(false) }
    val snap = snapshot()
    ScreenList("حسابي", onBack = onBack) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(user.fullName, 76.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(user.fullName, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Pill(user.role.label, Brand.Maroon)
                    Spacer(Modifier.height(4.dp))
                    Text(user.phone, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
        }
        item { MenuRow("الإشعارات", "${snap.notifications.count { !it.read }} غير مقروء", Icons.Outlined.Notifications) { nav.navigate("notifications") } }
        item { MenuRow("الإعلانات", "إعلانات المركز", Icons.Outlined.Campaign) { nav.navigate("announcements") } }
        if (user.role == Role.TEACHER) item { MenuRow("المكافآت", "منح النقاط ولوحة الشرف", Icons.Outlined.EmojiEvents) { nav.navigate("rewards") } }
        if (user.role == Role.PARENT) item { MenuRow("المكافآت", "نقاط وأوسمة أبنائي", Icons.Outlined.EmojiEvents) { nav.navigate("rewards") } }
        item { MenuRow("تغيير كلمة المرور", "حدّث كلمة المرور الخاصة بك", Icons.Outlined.Password) { changing = true } }
        item { MenuRow("عن التطبيق", "الإصدار وحالة المزامنة", Icons.Outlined.Info) { nav.navigate("about") } }
        item { MenuRow("تسجيل الخروج", "الخروج من هذا الجهاز", Icons.AutoMirrored.Outlined.Logout, Brand.Red) { askLogout = true } }
    }
    if (askLogout) ConfirmDialog("تسجيل الخروج؟", "يمكنك الدخول مرة أخرى برقم جوالك وكلمة المرور.", "خروج",
        { scope.launch { container.auth.signOut() } }, { askLogout = false }, destructive = true)
    if (changing) {
        var old by remember { mutableStateOf("") }
        var new1 by remember { mutableStateOf("") }
        var new2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text("تغيير كلمة المرور") },
            text = {
                Column {
                    Field(old, { old = it }, "كلمة المرور الحالية")
                    Spacer(Modifier.height(8.dp))
                    Field(new1, { new1 = it }, "كلمة المرور الجديدة")
                    Spacer(Modifier.height(8.dp))
                    Field(new2, { new2 = it }, "تأكيد كلمة المرور الجديدة")
                    if (new2.isNotEmpty() && new1 != new2) Text("غير متطابقتين", color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = new1.isNotEmpty() && new1 == new2, onClick = {
                    actions.run("تم تغيير كلمة المرور", { container.auth.changePassword(old, new1) }) { changing = false }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("إلغاء") } }
        )
    }
}
