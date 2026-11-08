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
fun AnnouncementsScreen(user: Member, nav: NavHostController) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val list = snap.announcements.sortedByDescending { it.createdAt }
    ScreenList("الإعلانات", onBack = { nav.popBackStack() }, fab = {
        if (Permissions.canPostAnnouncement(user)) AddFab("إعلان جديد") { nav.navigate("announce_new") }
    }) {
        if (list.isEmpty()) item { EmptyState("لا توجد إعلانات", "ستظهر هنا إعلانات المركز") }
        items(list, key = { it.id }) { a ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brand.MaroonSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Campaign, null, tint = Brand.Maroon)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text("${snap.recorder(a.byName, a.createdBy) ?: "الإدارة"} • ${Fmt.relative(a.createdAt)}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    Pill(a.audience.label, Brand.Navy)
                }
                Spacer(Modifier.height(10.dp))
                Text(a.body, style = MaterialTheme.typography.bodyMedium)
                if (user.role == Role.ADMIN || a.createdBy == user.id) {
                    var ask by remember { mutableStateOf(false) }
                    TextButton(onClick = { ask = true }) { Text("حذف", color = Brand.Red) }
                    if (ask) ConfirmDialog("حذف الإعلان؟", a.title, "حذف", { actions.run("حُذف الإعلان", { repo.deleteAnnouncement(a.id) }) }, { ask = false }, destructive = true)
                }
            }
        }
    }
}

@Composable
fun AnnounceNewScreen(user: Member, nav: NavHostController) {
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var aud by remember { mutableIntStateOf(0) }
    ScreenList("إعلان جديد", onBack = { nav.popBackStack() }) {
        item {
            AppCard {
                Field(title, { title = it }, "العنوان")
                Spacer(Modifier.height(10.dp))
                Field(body, { body = it }, "نص الإعلان", singleLine = false, minLines = 5)
                Spacer(Modifier.height(12.dp))
                Text("يصل إلى", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(listOf(Audience.ALL, Audience.TEACHERS).map { it.label }, aud, { aud = it })
            }
        }
        item {
            Button(
                onClick = {
                    actions.run("نُشر الإعلان وأُرسل إشعار", {
                        repo.postAnnouncement(Announcement("", title.trim(), body.trim(), listOf(Audience.ALL, Audience.TEACHERS)[aud], user.id))
                    }) { nav.popBackStack() }
                },
                enabled = Permissions.canPostAnnouncement(user) && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("نشر الإعلان") }
        }
    }
}

@Composable
fun NotificationsScreen(user: Member, nav: NavHostController, onBack: (() -> Unit)?) {
    val snap = snapshot()
    val actions = rememberActions()
    val repo = LocalContainer.current.center
    val list = snap.notifications.sortedByDescending { it.createdAt }
    ScreenList("الإشعارات", onBack = onBack, actions = {
        if (list.any { !it.read }) TextButton(onClick = { actions.run("تم تعليم الكل كمقروء", { repo.markAllNotificationsRead() }) }) { Text("قراءة الكل") }
    }) {
        if (list.isEmpty()) item { EmptyState("لا توجد إشعارات", "ستصلك هنا تحديثات الحفظ والحضور والإعلانات") }
        items(list, key = { it.id }) { n ->
            val icon = when (n.kind) {
                "record" -> Icons.Outlined.AutoStories
                "absence" -> Icons.Outlined.EventBusy
                "reward" -> Icons.Outlined.EmojiEvents
                "announcement" -> Icons.Outlined.Campaign
                else -> Icons.Outlined.Notifications
            }
            AppCard(padding = 12.dp, onClick = {
                if (!n.read) actions.run(null, { repo.markNotificationRead(n.id) })
                when (n.kind) {
                    "announcement" -> nav.navigate("announcements")
                    "absence" -> if (snap.student(n.refId) != null) nav.navigate("student/${n.refId}")
                    else -> {}
                }
            }) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (n.read) Brand.Line else Brand.NavySoft),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = if (n.read) Brand.Muted else Brand.Navy) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold)
                        Text(n.body, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        Text(Fmt.relative(n.createdAt), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                    }
                    if (!n.read) Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(Brand.Maroon))
                }
            }
        }
    }
}
