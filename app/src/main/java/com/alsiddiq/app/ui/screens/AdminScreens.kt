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
fun AdminHome(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val demo = isDemo()
    val stats = remember(snap) { Stats.forCenter(snap, snap.students) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HomeHeader(
                "السلام عليكم ورحمة الله", snap.center.name,
                Fmt.dayTitle(today) + " • " + Fmt.hijri(today),
                snap.notifications.count { !it.read }
            ) { nav.navigate("notifications") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                StatusBanners(Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(10.dp))
                RoleStrip(user)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("الطلاب", "${stats.students}", Icons.Outlined.School, Brand.Navy, Modifier.weight(1f), onClick = { nav.navigate("students") })
                    StatTile("الحلقات", "${snap.circles.size}", Icons.Outlined.Groups, Brand.Maroon, Modifier.weight(1f), onClick = { nav.navigate("circles") })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("تسميع اليوم", "${stats.recordsToday}", Icons.Outlined.AutoStories, Brand.Green, Modifier.weight(1f), sub = "${stats.ayatThisWeek} آية هذا الأسبوع")
                    StatTile("حضور اليوم", if (stats.attendanceToday > 0) Fmt.pct(stats.attendanceToday) else "—", Icons.Outlined.FactCheck, Brand.Gold, Modifier.weight(1f),
                        sub = if (stats.attendanceToday > 0) null else "لم يُسجَّل بعد", onClick = { nav.navigate("attendance") })
                }

                SectionHeader("إجراءات سريعة")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("طالب جديد", Icons.Outlined.PersonAdd, Modifier.weight(1f)) { nav.navigate("student_edit/new") }
                    QuickAction("حلقة جديدة", Icons.Outlined.GroupAdd, Modifier.weight(1f)) { nav.navigate("circle_edit/new") }
                    QuickAction("تسجيل", Icons.Outlined.EditNote, Modifier.weight(1f)) { nav.navigate("record_start") }
                    QuickAction("معلم", Icons.Outlined.CoPresent, Modifier.weight(1f)) { nav.navigate("teacher_edit/new") }
                }

                SectionHeader("نشاط التسميع", "التقارير") { nav.navigate("reports") }
                AppCard {
                    Text("عدد السجلات يومياً — آخر 14 يوماً", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    Spacer(Modifier.height(10.dp))
                    BarChart(stats.dailyRecords.map { (d, n) -> BarDatum(shortDayLabel(d) + " " + d.dayOfMonth, n.toFloat(), "$n سجل") })
                }
                Spacer(Modifier.height(10.dp))
                AppCard {
                    Text("توزيع التقديرات", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    DonutChart(
                        Grade.entries.map { Slice(it.label, (stats.gradeDistribution[it] ?: 0).toFloat(), gradeColor(it)) },
                        centerText = "${stats.gradeDistribution.values.sum()}"
                    )
                }

                SectionHeader("الحلقات", "عرض الكل") { nav.navigate("circles") }
            }
        }
        items(snap.circles) { c ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { CircleCard(c, snap) { nav.navigate("circle/${c.id}") } }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("الأكثر إنجازاً هذا الأسبوع")
                val weekStart = LocalDate.now().minusDays(6)
                val top = snap.students.map { s ->
                    s to snap.recitations.filter { it.studentId == s.id && it.kind == RecordKind.HIFZ && !Stats.dayOf(it.recordedAt).isBefore(weekStart) }.sumOf { Stats.ayatOf(it) }
                }.sortedByDescending { it.second }.take(5)
                AppCard(padding = 8.dp) {
                    top.forEachIndexed { i, (s, ayat) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("student/${s.id}") }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (i == 0) Brand.Gold else Brand.NavySoft),
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", color = if (i == 0) Color.White else Brand.Navy, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            Text(s.fullName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("$ayat آية", style = MaterialTheme.typography.labelLarge, color = Brand.Navy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick, padding = 10.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.MaroonSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.Maroon)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun CircleCard(c: Circle, snap: CenterSnapshot, onClick: () -> Unit) {
    val students = snap.studentsIn(c.id)
    val todayIso = LocalDate.now().toString()
    val att = snap.attendance.filter { it.circleId == c.id && it.day == todayIso }
    val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
    AppCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Groups, null, tint = Brand.Navy)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Text(snap.teacherOf(c) ?: "بدون معلم", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                if (c.schedule.isNotBlank()) Text(c.schedule, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill("${students.size} طالب", Brand.Navy)
                if (c.level.isNotBlank()) { Spacer(Modifier.height(4.dp)); Pill(c.level, Brand.Maroon) }
            }
        }
        if (att.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("حضور اليوم $present/${att.size}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                Spacer(Modifier.width(8.dp))
                LinearMeter(present.toFloat() / att.size, Brand.Green, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MoreScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    ScreenList("الإدارة") {
        item { RoleStrip(user) }
        item { SectionHeader("المعلمون وكلمات السر") }
        item { MenuRow("المعلمون", "${snap.activeTeacherNames().size} اسم • لمعرفة من سجّل", Icons.Outlined.CoPresent, Brand.Maroon) { nav.navigate("teachers") } }
        item { MenuRow("كلمات السر", "العامة والمعلمين والإدارة", Icons.Outlined.Password) { nav.navigate("passwords") } }
        item { SectionHeader("الأدوات") }
        items(Tools.all) { t -> val ctx = androidx.compose.ui.platform.LocalContext.current; MenuRow(t.title, t.sub, t.icon, t.color) { t.open(ctx) } }
        item { SectionHeader("المتابعة") }
        item { MenuRow("الحضور والغياب", "تسجيل ومراجعة الحضور", Icons.Outlined.FactCheck) { nav.navigate("attendance") } }
        items(snap.circles.sortedBy { it.name }.take(6)) { c -> MenuRow("تقرير ${c.name}", "كم حفظوا ومن غاب", Icons.Outlined.Summarize, Brand.Gold) { nav.navigate("circle_report/${c.id}") } }
        item { MenuRow("المكافآت والنقاط", "لوحة الشرف ومنح النقاط", Icons.Outlined.EmojiEvents) { nav.navigate("rewards") } }
        item { MenuRow("الإعلانات والتقارير", "${snap.announcements.size} منشور", Icons.Outlined.Campaign) { nav.navigate("announcements") } }
        item { MenuRow("الإشعارات", "${snap.notifications.count { !it.read }} غير مقروء", Icons.Outlined.Notifications) { nav.navigate("notifications") } }
        item { SectionHeader("المركز") }
        item { MenuRow("بيانات المركز", snap.center.name, Icons.Outlined.Mosque) { nav.navigate("settings") } }
        item { MenuRow("عن التطبيق", "الإصدار وحالة المزامنة", Icons.Outlined.Info) { nav.navigate("about") } }
    }
}

@Composable
fun MenuRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Brand.Navy, onClick: () -> Unit) {
    AppCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Brand.Muted)
        }
    }
}

@Composable
fun CenterSettingsScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    var name by remember { mutableStateOf(snap.center.name) }
    var city by remember { mutableStateOf(snap.center.city) }
    ScreenList("بيانات المركز", onBack = { nav.popBackStack() }) {
        item {
            AppCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.logo), "الشعار", Modifier.size(110.dp))
                }
                Spacer(Modifier.height(12.dp))
                Field(name, { name = it }, "اسم المركز")
                Spacer(Modifier.height(10.dp))
                Field(city, { city = it }, "الحي / المدينة")
            }
        }
        item {
            Button(
                onClick = { actions.run("تم الحفظ", { repo.updateCenter(CenterInfo(name.trim(), city.trim())) }) { nav.popBackStack() } },
                enabled = Permissions.canManageCenter(user),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("حفظ") }
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val demo = isDemo()
    ScreenList("عن التطبيق", onBack = { nav.popBackStack() }) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp))
                    Text("مركز الصديق لتعليم القرآن الكريم", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text("الإصدار ${com.alsiddiq.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("مصدر البيانات", if (demo) "تجريبي (على الجهاز)" else "قاعدة البيانات المركزية")
                val sync = container.center.syncState.collectAsStateWithLifecycle().value
                KeyValueRow("المزامنة", when {
                    demo -> "غير مفعّلة (وضع تجريبي)"
                    sync is com.alsiddiq.app.data.SyncState.Pending -> "${sync.count} تعديل بانتظار الاتصال"
                    else -> "متزامن مع الخادم"
                })
                KeyValueRow("الخادم مُعدّ", if (container.cloudConfigured) "نعم" else "لا")
            }
        }
        item { StatusBanners() }
        if (!demo) item {
            val actions = rememberActions()
            OutlinedButton(onClick = { actions.run("تمت المزامنة", { container.center.refresh() }) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Sync, null); Spacer(Modifier.width(6.dp)); Text("مزامنة الآن")
            }
        }
    }
}
