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
import androidx.compose.ui.graphics.asImageBitmap
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
                // v3.8: the admin's photo on the announcement (tap = full view)
                a.imagePath?.let { p -> Spacer(Modifier.height(10.dp)); AnnouncementImage(p, a.title) }
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
    // v3.8 (his request, 10:24 PM): the admin can add a photo (gallery or camera)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = PhotoUtil.decode(ctx, uri) }
    val shotFile = remember { java.io.File(java.io.File(ctx.cacheDir, "photos_tmp").apply { mkdirs() }, "announcement.jpg") }
    val shotUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".photos", shotFile) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photo = PhotoUtil.decode(ctx, shotUri) }
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
                if (user.role == Role.ADMIN) {
                    Spacer(Modifier.height(12.dp))
                    Text("صورة (اختياري)", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    Spacer(Modifier.height(6.dp))
                    photo?.let { b ->
                        val ib = remember(b) { b.asImageBitmapCompat() }
                        Image(ib, null, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            label = { Text("المعرض") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = { runCatching { camera.launch(shotUri) } },
                            label = { Text("الكاميرا") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)) })
                        if (photo != null) AssistChip(onClick = { photo = null }, label = { Text("إزالة", color = Brand.Red) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = Brand.Red) })
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    busy = true
                    val a = Announcement("", title.trim(), body.trim(), listOf(Audience.ALL, Audience.TEACHERS)[aud], user.id)
                    val b = photo
                    actions.run("نُشر الإعلان وأُرسل إشعار", {
                        if (b != null) repo.postAnnouncementWithImage(a, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PhotoUtil.compress(b, 1280, 280_000) })
                        else repo.postAnnouncement(a)
                    }, onError = { busy = false }) { nav.popBackStack() }
                },
                enabled = Permissions.canPostAnnouncement(user) && title.isNotBlank() && !busy,
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
    // v3.6: the admin's «تنبيهات المعلمين» (a teacher registered or dismissed a student), newest first.
    val events = if (Permissions.canSeeEvents(user)) snap.events.sortedByDescending { it.createdAt } else emptyList()
    ScreenList("الإشعارات", onBack = onBack, actions = {
        if (list.any { !it.read } || events.any { it.unread }) TextButton(onClick = {
            actions.run("تم تعليم الكل كمقروء", {
                val a = if (list.any { !it.read }) repo.markAllNotificationsRead() else Result.success(Unit)
                if (events.any { it.unread }) repo.markEventsRead(null) else a
            })
        }) { Text("قراءة الكل") }
    }) {
        if (events.isNotEmpty()) {
            item { SectionHeader("تنبيهات المعلمين") }
            items(events, key = { "ev_" + it.id }) { e -> AdminEventRow(e, snap) {
                if (e.unread) actions.run(null, { repo.markEventsRead(e.id) })
                if (e.studentId != null && snap.student(e.studentId) != null) nav.navigate("student/${e.studentId}")
            } }
            if (list.isNotEmpty()) item { SectionHeader("الإشعارات") }
        }
        if (list.isEmpty() && events.isEmpty()) item { EmptyState("لا توجد إشعارات", "ستصلك هنا تحديثات الحفظ والحضور والإعلانات") }
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


/** «الأستاذ عبدالله سجّل طالب: محمد أحمد — حلقة النور». */
fun adminEventText(e: AdminEvent): String {
    val who = e.actorName.trim().let { n -> if (n.isEmpty()) "معلم" else if (n.startsWith("الأستاذ") || n.startsWith("الشيخ") || n.startsWith("الإدارة")) n else "الأستاذ $n" }
    val verb = if (e.kind == "student_dismissed") "فصل" else "سجّل"
    return "$who $verb طالب: ${e.studentName}" + (if (e.circleName.isNotBlank()) " — ${e.circleName}" else "")
}

@Composable
private fun AdminEventRow(e: AdminEvent, snap: CenterSnapshot, onClick: () -> Unit) {
    val added = e.kind != "student_dismissed"
    val tint = if (added) Brand.Green else Brand.Red
    AppCard(padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = if (e.unread) 0.16f else 0.08f)), contentAlignment = Alignment.Center) {
                Icon(if (added) Icons.Outlined.PersonAdd else Icons.Outlined.PersonRemove, null, tint = tint)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(adminEventText(e), style = MaterialTheme.typography.titleSmall, fontWeight = if (e.unread) FontWeight.Bold else FontWeight.Medium)
                if (e.phone.isNotBlank()) Text("الجوال: ${e.phone}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(Fmt.relative(e.createdAt), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
            }
            if (e.unread) Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(Brand.Maroon))
        }
    }
}


private fun android.graphics.Bitmap.asImageBitmapCompat(): androidx.compose.ui.graphics.ImageBitmap = this.asImageBitmap()

/** v3.8: an announcement's photo (cached like the student photos); tap opens it whole. */
@Composable
fun AnnouncementImage(path: String, title: String, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 260.dp) {
    val repo = LocalContainer.current.center
    var bmp by remember(path) { mutableStateOf(PhotoCache.get(path)) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (bmp == null) {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { repo.announcementImage(path) }.getOrNull() }
            bmp = bytes?.let { b -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
                ?.also { PhotoCache.put(path, it) }
        }
    }
    val b = bmp
    if (b == null) Box(modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, null, tint = Brand.Muted)
    } else Image(b, title, modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(14.dp)).clickable { full = true },
        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    if (full && b != null) androidx.compose.ui.window.Dialog(onDismissRequest = { full = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { full = false }, contentAlignment = Alignment.Center) {
            Image(b, title, Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
    }
}
