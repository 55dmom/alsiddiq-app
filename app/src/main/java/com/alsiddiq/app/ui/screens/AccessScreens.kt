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


// ======================= v3: general view, Settings with «الإدارة» and «المعلمين», teachers, passwords, reports =======================

/** Home for the general (read-only) password: the whole centre at a glance. */
@Composable
fun ViewerHome(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val stats = remember(snap) { Stats.forCenter(snap, snap.students) }
    val per = remember(snap) { snap.students.map { it to Stats.forStudent(it, snap) }.sortedByDescending { it.second.memorisedAyat } }
    val totalJuz = per.sumOf { it.second.juzEquivalent }
    val today = LocalDate.now()
    val reports = snap.announcements.sortedByDescending { it.createdAt }.take(3)
    // His request (Nov 8, 5:42 PM): a realistic, blurred background photo on the home screen (not inside his tools).
    LazyColumn(Modifier.fillMaxSize().homeBackdrop(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HomeHeader("السلام عليكم ورحمة الله", snap.center.name, Fmt.dayTitle(today) + " • " + Fmt.hijri(today), snap.notifications.count { !it.read }) { nav.navigate("notifications") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatusBanners(Modifier.padding(top = 12.dp))
                com.alsiddiq.app.ui.components.OccasionBanner(Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("وضع العرض: تصفّح فقط. التعديل من الإعدادات ← الإدارة أو المعلمين", style = MaterialTheme.typography.bodySmall, color = Brand.Ink)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("الطلاب", "${stats.students}", Icons.Outlined.School, Brand.Navy, Modifier.weight(1f), onClick = { nav.navigate("students") })
                    StatTile("الحلقات", "${snap.circles.size}", Icons.Outlined.Groups, Brand.Maroon, Modifier.weight(1f), onClick = { nav.navigate("circles") })
                    StatTile("المعلمون", "${snap.teacherCount()}", Icons.Outlined.CoPresent, Brand.Gold, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("المحفوظ (أجزاء)", Fmt.one(totalJuz), Icons.Outlined.MenuBook, Brand.Green, Modifier.weight(1f), sub = "لكل الطلاب")
                    StatTile("آيات الأسبوع", "${stats.ayatThisWeek}", Icons.Outlined.AutoStories, Brand.Navy, Modifier.weight(1f))
                    StatTile("حضور اليوم", if (stats.attendanceToday > 0) Fmt.pct(stats.attendanceToday) else "—", Icons.Outlined.FactCheck, Brand.Maroon, Modifier.weight(1f))
                }
                SectionHeader("الأدوات")
                ToolsRow()
                SectionHeader("التفاعل", "التقارير") { nav.navigate("reports") }
                AppCard {
                    Text("عدد السجلات يومياً — آخر 14 يوماً", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    Spacer(Modifier.height(10.dp))
                    BarChart(stats.dailyRecords.map { (d, n) -> BarDatum(shortDayLabel(d) + " " + d.dayOfMonth, n.toFloat(), "$n سجل") })
                }
                SectionHeader("كم حفظ كل طالب", "كل الطلاب") { nav.navigate("students") }
            }
        }
        if (per.isEmpty()) item { Box(Modifier.padding(16.dp)) { EmptyState("لا يوجد طلاب بعد", "تضيفهم الإدارة أو المعلمون") } }
        items(per.take(10), key = { it.first.id }) { (s, st) ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                AppCard(onClick = { nav.navigate("student/${s.id}") }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentAvatar(s, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${snap.circle(s.circleId)?.name ?: "بدون حلقة"} • ${st.memorisedAyat} آية", style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            LinearMeter((st.juzEquivalent / 30.0).toFloat().coerceIn(0f, 1f), Brand.Gold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.one(st.juzEquivalent), style = MaterialTheme.typography.titleMedium, color = Brand.Navy)
                            Text("جزء", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                        }
                    }
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 16.dp)) { SectionHeader("الحلقات", "عرض الكل") { nav.navigate("circles") } } }
        items(snap.circles) { c -> Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { CircleCard(c, snap) { nav.navigate("circle/${c.id}") } } }
        if (reports.isNotEmpty()) {
            item { Column(Modifier.padding(horizontal = 16.dp)) { SectionHeader("آخر التقارير والإعلانات", "الكل") { nav.navigate("announcements") } } }
            items(reports, key = { it.id }) { a ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    AppCard(onClick = { nav.navigate("announcements") }, padding = 12.dp) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.body, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.relative(a.createdAt), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigSectionButton(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, soft: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Brand.Card, shadowElevation = 2.dp,
        modifier = modifier.border(1.dp, Brand.Line, RoundedCornerShape(22.dp))) {
        Column(Modifier.padding(vertical = 22.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(soft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Brand.Muted, textAlign = TextAlign.Center)
        }
    }
}

/** Settings for the general view: the two buttons «الإدارة» and «المعلمين». */
@Composable
fun ViewerSettingsScreen(user: Member, nav: NavHostController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val snap = snapshot()
    var askLogout by remember { mutableStateOf(false) }
    ScreenList("الإعدادات") {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                BigSectionButton("الإدارة", "كلمة سر الإدارة\nتحكم كامل", Icons.Outlined.AdminPanelSettings, Brand.Navy, Brand.NavySoft, Modifier.weight(1f)) { nav.navigate("role_login/ADMIN") }
                BigSectionButton("المعلمين", "كلمة سرك الخاصة\nلحلقتك", Icons.Outlined.CoPresent, Brand.Maroon, Brand.MaroonSoft, Modifier.weight(1f)) { nav.navigate("role_login/TEACHER") }
            }
        }
        item { SectionHeader("الأدوات") }
        items(Tools.all) { t -> val ctx = androidx.compose.ui.platform.LocalContext.current; MenuRow(t.title, t.sub, t.icon, t.color) { t.open(ctx) } }
        item { SectionHeader("المظهر") }
        item { ThemesMenuItem() }
        item { com.alsiddiq.app.ui.about.CenterInfoEntries() }
        item { SectionHeader("عام") }
        item { MenuRow("المتفوقون", "أعلى الطلاب حسب تقاريرهم", Icons.Outlined.EmojiEvents, Brand.Gold) { nav.navigate("honors") } }
        item { MenuRow("التقارير والإعلانات", "${snap.announcements.size} منشور", Icons.Outlined.Campaign) { nav.navigate("announcements") } }
        item { MenuRow("الإشعارات", "${snap.notifications.count { !it.read }} غير مقروء", Icons.Outlined.Notifications) { nav.navigate("notifications") } }
        item { MenuRow("عن التطبيق", "الإصدار وحالة المزامنة", Icons.Outlined.Info) { nav.navigate("about") } }
        item { MenuRow("قفل التطبيق", "يطلب كلمة السر عند الفتح القادم", Icons.Outlined.Lock, Brand.Red) { askLogout = true } }
    }
    if (askLogout) ConfirmDialog("قفل التطبيق؟", "ستحتاج كلمة السر العامة للدخول مرة أخرى.", "قفل",
        { scope.launch { container.auth.signOut() } }, { askLogout = false }, destructive = true)
}

/** A strip at the top of the admin and teacher sections: who is signed in, and a way out. */
@Composable
fun RoleStrip(user: Member) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val base by container.auth.baseUser.collectAsStateWithLifecycle()
    val admin = user.role == Role.ADMIN
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (admin) Brand.NavySoft else Brand.MaroonSoft).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (admin) Icons.Outlined.AdminPanelSettings else Icons.Outlined.CoPresent, null, tint = if (admin) Brand.Navy else Brand.Maroon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        val mine = Permissions.ownCircles(user, snapshot())
        Text(if (admin) "أنت في قسم الإدارة" else "قسم المعلمين: ${user.fullName}" + (if (mine.isNotEmpty()) " • " + mine.joinToString("، ") { it.name } else ""), style = MaterialTheme.typography.bodySmall, color = Brand.Ink, modifier = Modifier.weight(1f))
        TextButton(onClick = { scope.launch { container.auth.leaveRole() } }) {
            Text(if (base != null) "خروج للعرض العام" else "خروج", color = Brand.Red)
        }
    }
}

/** Teacher section: their tools. */
@Composable
fun TeacherMoreScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val mine = Permissions.ownCircles(user, snap)   // v3.3: his own circle(s)
    ScreenList("المزيد") {
        item { RoleStrip(user) }
        item { SectionHeader("تقرير اليوم") }
        if (mine.isEmpty()) item { EmptyState("لم تُسند لك حلقة بعد", "تسند الإدارة الحلقة لكل معلم من الإعدادات ← الإدارة ← المعلمون") }
        else item { MenuRow("تقرير اليوم", "اختر الحلقة: كم حفظوا اليوم ومن غاب", Icons.Outlined.Summarize, Brand.Maroon) { nav.navigate("circle_report/${mine.first().id}") } }
        item { SectionHeader("الأدوات") }
        items(Tools.all) { t -> val ctx = androidx.compose.ui.platform.LocalContext.current; MenuRow(t.title, t.sub, t.icon, t.color) { t.open(ctx) } }
        item { SectionHeader("المظهر") }
        item { ThemesMenuItem() }
        item { com.alsiddiq.app.ui.about.CenterInfoEntries() }
        item { SectionHeader("أدوات المعلم") }
        item { MenuRow("طالب جديد", "إضافة سجل طالب في حلقتك", Icons.Outlined.PersonAdd) { nav.navigate("student_edit/new" + (mine.firstOrNull()?.let { "?circle=${it.id}" } ?: "")) } }
        item { MenuRow("كل الحلقات", "عرض فقط: حلقات المعلمين الآخرين وطلابهم", Icons.Outlined.Groups) { nav.navigate("circles") } }
        item { MenuRow("التقارير والإحصائيات", "رسوم الحلقات", Icons.Outlined.Insights) { nav.navigate("reports") } }
        item { MenuRow("المتفوقون", "أعلى الطلاب حسب تقاريرهم", Icons.Outlined.EmojiEvents, Brand.Gold) { nav.navigate("honors") } }
        item { MenuRow("المكافآت", "منح النقاط ولوحة الشرف", Icons.Outlined.EmojiEvents) { nav.navigate("rewards") } }
        item { MenuRow("الإعلانات", "إعلانات المركز", Icons.Outlined.Campaign) { nav.navigate("announcements") } }
        item { MenuRow("الإشعارات", "${snap.notifications.count { !it.read }} غير مقروء", Icons.Outlined.Notifications) { nav.navigate("notifications") } }
        item { MenuRow("عن التطبيق", "الإصدار وحالة المزامنة", Icons.Outlined.Info) { nav.navigate("about") } }
    }
}

/** Admin (v3.3): the teachers — each with his own password and his circle. */
@Composable
fun TeachersScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val list = snap.members.filter { it.role == Role.TEACHER && !it.allCircles }.sortedWith(compareBy({ !it.active }, { it.fullName }))
    ScreenList("المعلمون", onBack = { nav.popBackStack() }, fab = {
        if (Permissions.canManageMembers(user)) AddFab("معلم جديد") { nav.navigate("teacher_edit/new") }
    }) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = Brand.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("لكل معلم كلمة سر خاصة: يعدّل بها حلقته فقط، ويشاهد بقية الحلقات للعرض. يدخل بها من الإعدادات ← المعلمين.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Ink, modifier = Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) item { EmptyState("لا يوجد معلمون بعد", "أضف أول معلم واختر حلقته وكلمة سره") }
        items(list, key = { it.id }) { m ->
            val circles = snap.circlesOf(m.id)
            AppCard(onClick = { nav.navigate("teacher_edit/${m.id}") }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.fullName, color = if (m.active) Brand.Maroon else Brand.Muted)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(circles.joinToString("، ") { "${it.name} (${ArabicText.students(snap.studentsIn(it.id).size)})" }.ifBlank { "بدون حلقة" },
                            style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!m.active) Pill("موقوف", Brand.Red)
                }
            }
        }
    }
}

/** Admin (v3.3): add or edit one teacher: name, circle, password; stop him (his records stay). */
@Composable
fun TeacherEditScreen(user: Member, nav: NavHostController, id: String) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val existing = snap.member(id)?.takeIf { it.role == Role.TEACHER }
    var name by remember { mutableStateOf(existing?.fullName ?: "") }
    var circleId by remember { mutableStateOf(existing?.let { snap.circlesOf(it.id).firstOrNull()?.id }) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    var askRemove by remember { mutableStateOf(false) }
    val stopped = existing != null && !existing.active
    ScreenList(if (existing == null) "معلم جديد" else "تعديل المعلم", onBack = { nav.popBackStack() }) {
        if (stopped) item { ReadOnlyNote("هذا المعلم موقوف. اختر له كلمة سر جديدة واحفظ لتشغيله من جديد.") }
        item {
            AppCard {
                Field(name, { name = it }, "اسم المعلم")
                Spacer(Modifier.height(10.dp))
                SelectorField("الحلقة", snap.circle(circleId)?.let { "${it.name} (${ArabicText.students(snap.studentsIn(it.id).size)})" } ?: "اختر الحلقة") { pick = true }
                Spacer(Modifier.height(12.dp))
                Text(if (existing == null || stopped) "كلمة سر المعلم" else "كلمة سر جديدة (اتركها فارغة لإبقاء الحالية)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                com.alsiddiq.app.ui.auth.PasswordField(pass, { pass = it }, "كلمة السر (6 على الأقل)")
                Spacer(Modifier.height(8.dp))
                com.alsiddiq.app.ui.auth.PasswordField(pass2, { pass2 = it }, "تأكيد كلمة السر")
                if (pass2.isNotEmpty() && pass != pass2) Text("غير متطابقتين", color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("تُحفظ مشفّرة على الخادم. لا يمكن عرضها لاحقاً، فقط تغييرها. تغييرها يُخرج المعلم من أي جهاز داخل بها.", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
        }
        item {
            Button(
                enabled = Permissions.canManageMembers(user) && name.isNotBlank() && pass == pass2 && ((existing != null && !stopped) || pass.length >= 6),
                onClick = {
                    actions.run(if (existing == null) "تمت إضافة المعلم" else if (stopped) "عاد المعلم للعمل" else "تم حفظ التعديلات", {
                        repo.saveTeacher(existing?.id, name, circleId, pass.ifBlank { null })
                    }) { nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(if (existing == null) "إضافة المعلم" else if (stopped) "تشغيل المعلم وحفظ" else "حفظ") }
            if (existing != null && existing.active) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { askRemove = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.Red)) { Text("إيقاف المعلم (تبقى سجلاته)") }
            }
        }
    }
    if (pick) PickerDialog("اختر الحلقة", listOf(Circle("", "بدون حلقة", null)) + snap.circles.sortedBy { it.name },
        { c -> c.name + (snap.member(c.teacherId)?.takeIf { it.id != existing?.id && c.id.isNotEmpty() }?.let { " — مع ${it.fullName}" } ?: "") },
        { circleId = it.id.ifEmpty { null } }, { pick = false })
    if (askRemove && existing != null) ConfirmDialog("إيقاف ${existing.fullName}؟", "لن يستطيع الدخول بكلمة سره بعد الآن، وتبقى حلقته بدون معلم. سجلاته السابقة تبقى، وتقدر تشغّله لاحقاً بكلمة سر جديدة.", "إيقاف",
        { actions.run("تم إيقاف المعلم", { repo.removeTeacher(existing.id) }) { nav.popBackStack() } }, { askRemove = false }, destructive = true)
}

/** Admin: the general password and the admin password. (v3.3: each teacher's password is on his own page.) */
@Composable
fun PasswordsScreen(user: Member, nav: NavHostController) {
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    var editing by remember { mutableStateOf<Role?>(null) }
    ScreenList("كلمات السر", onBack = { nav.popBackStack() }) {
        item { Text("كلمات السر محفوظة مشفّرة على الخادم ولا يمكن عرضها، فقط تغييرها. عند تغيير أي كلمة سر يخرج كل من كان داخلاً بها فوراً.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
        item { MenuRow("كلمة السر العامة", "للعرض فقط: الطلاب والنتائج والتقارير", Icons.Outlined.Visibility, Brand.Gold) { editing = Role.VIEWER } }
        item { MenuRow("كلمة سر الإدارة", "تحكم كامل في التطبيق", Icons.Outlined.AdminPanelSettings) { editing = Role.ADMIN } }
        item { MenuRow("كلمات سر المعلمين", "لكل معلم كلمة سر من صفحته في «المعلمون»", Icons.Outlined.CoPresent, Brand.Maroon) { nav.navigate("teachers") } }
    }
    editing?.let { target ->
        var p1 by remember(target) { mutableStateOf("") }
        var p2 by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (target == Role.ADMIN) "كلمة سر الإدارة الجديدة" else "كلمة السر العامة الجديدة") },
            text = {
                Column {
                    com.alsiddiq.app.ui.auth.PasswordField(p1, { p1 = it }, "كلمة السر الجديدة (6 على الأقل)")
                    Spacer(Modifier.height(8.dp))
                    com.alsiddiq.app.ui.auth.PasswordField(p2, { p2 = it }, "تأكيد كلمة السر")
                    if (p2.isNotEmpty() && p1 != p2) Text("غير متطابقتين", color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = p1.length >= 6 && p1 == p2, onClick = {
                    actions.run("تم تغيير كلمة السر", { repo.setAccessPassword(target, p1) }) { editing = null }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } }
        )
    }
}

/** Teacher (or admin): today's circle report — how much was memorised and who was absent. Publish it for everyone, or share it. */
@Composable
fun CircleReportScreen(user: Member, nav: NavHostController, initialCircleId: String) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var circleId by remember { mutableStateOf(initialCircleId) }
    val circle = snap.circle(circleId)
    var pickCircle by remember { mutableStateOf(false) }
    val choices = Permissions.ownCircles(user, snap)
    var day by remember { mutableStateOf(LocalDate.now()) }
    val text = remember(snap, day, circleId) { circleReportText(snap, circleId, day) }
    var body by remember(text) { mutableStateOf(text) }
    val canPost = Permissions.canPostAnnouncement(user) && (user.role == Role.ADMIN || Permissions.teachesCircle(user, circle))
    ScreenList("تقرير اليوم", onBack = { nav.popBackStack() }) {
        item { SelectorField("الحلقة", circle?.let { "${it.name} (${ArabicText.students(snap.studentsIn(it.id).size)})" } ?: "اختر الحلقة") { pickCircle = true } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L to "اليوم", 1L to "أمس").forEach { (back, label) ->
                    FilterChip(selected = day == LocalDate.now().minusDays(back), onClick = { day = LocalDate.now().minusDays(back) }, label = { Text(label) })
                }
            }
        }
        item {
            AppCard {
                Text(Fmt.dayTitle(day), style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(6.dp))
                Field(body, { body = it }, "نص التقرير (يمكنك التعديل)", singleLine = false, minLines = 8)
            }
        }
        item {
            Button(enabled = canPost && body.isNotBlank(), onClick = {
                actions.run("نُشر التقرير للجميع", {
                    repo.postAnnouncement(Announcement("", "تقرير ${circle?.name ?: ""} — ${Fmt.date(day)}", body.trim(), Audience.ALL, user.id))
                }) { nav.popBackStack() }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("نشر التقرير في التطبيق") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "تقرير ${circle?.name ?: ""} — ${Fmt.date(day)}\n\n" + body.trim())
                ctx.startActivity(android.content.Intent.createChooser(send, "مشاركة التقرير"))
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("مشاركة (واتساب وغيره)") }
        }
    }
    if (pickCircle) PickerDialog("اختر الحلقة", choices, { "${it.name} (${ArabicText.students(snap.studentsIn(it.id).size)})" }, { circleId = it.id }, { pickCircle = false })
}

fun circleReportText(snap: CenterSnapshot, circleId: String, day: LocalDate): String {
    val students = snap.studentsIn(circleId).sortedBy { it.fullName }
    val ids = students.map { it.id }.toSet()
    val iso = day.toString()
    val att = snap.attendance.filter { it.studentId in ids && it.day == iso }.associateBy { it.studentId }
    val recs = snap.recitations.filter { it.studentId in ids && Stats.dayOf(it.recordedAt) == day }
    val present = att.values.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
    val absent = students.filter { att[it.id]?.status == AttendanceStatus.ABSENT }
    val excused = students.filter { att[it.id]?.status == AttendanceStatus.EXCUSED }
    val sb = StringBuilder()
    val by = (recs.mapNotNull { it.byName } + att.values.mapNotNull { it.byName }).distinct()
    if (by.isNotEmpty()) sb.append("المعلم: ${by.joinToString("، ")}\n")
    sb.append("عدد الطلاب: ${students.size}\n")
    if (att.isEmpty()) sb.append("الحضور: لم يُسجَّل بعد\n") else sb.append("الحضور: $present من ${students.size}\n")
    sb.append("الغائبون: ").append(absent.joinToString("، ") { it.fullName }.ifBlank { "لا أحد" }).append("\n")
    if (excused.isNotEmpty()) sb.append("المستأذنون: ").append(excused.joinToString("، ") { it.fullName }).append("\n")
    val hifz = recs.filter { it.kind == RecordKind.HIFZ }
    val rev = recs.filter { it.kind != RecordKind.HIFZ }
    sb.append("\nالحفظ الجديد: ${hifz.sumOf { Stats.ayatOf(it) }} آية\n")
    hifz.groupBy { it.studentId }.forEach { (sid, rs) ->
        sb.append("• ${snap.student(sid)?.fullName ?: ""}: ").append(rs.joinToString("، ") { Stats.describeRange(it) + " (" + it.grade.label + ")" }).append("\n")
    }
    sb.append("\nالمراجعة والتسميع: ${rev.sumOf { Stats.ayatOf(it) }} آية\n")
    rev.groupBy { it.studentId }.forEach { (sid, rs) ->
        sb.append("• ${snap.student(sid)?.fullName ?: ""}: ").append(rs.joinToString("، ") { Stats.describeRange(it) + " (" + it.grade.label + ")" }).append("\n")
    }
    val none = students.filter { s -> recs.none { it.studentId == s.id } && att[s.id]?.status != AttendanceStatus.ABSENT }
    if (none.isNotEmpty()) sb.append("\nلم يُسمِّعوا: ").append(none.joinToString("، ") { it.fullName }).append("\n")
    return sb.toString().trim()
}

/** «الثيمات» row in Settings: open to everyone (no password needed to change colours). */
@Composable
fun ThemesMenuItem() {
    var open by remember { mutableStateOf(false) }
    MenuRow("الثيمات", com.alsiddiq.app.ui.components.currentThemeLabel(), Icons.Outlined.Palette, Brand.Gold) { open = true }
    if (open) com.alsiddiq.app.ui.components.ThemePickerSheet { open = false }
}
