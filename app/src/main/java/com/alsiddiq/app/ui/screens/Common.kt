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
fun snapshot(): CenterSnapshot = LocalContainer.current.center.snapshot.collectAsStateWithLifecycle().value

@Composable
fun isDemo(): Boolean = LocalContainer.current.auth.mode == BackendMode.DEMO

/** Standard screen frame: top bar + scrolling content with comfortable padding. */
@Composable
fun ScreenList(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = Brand.Bg,
        topBar = { ScreenTopBar(title, onBack, actions) },
        floatingActionButton = { fab?.invoke() }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun AddFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Brand.Maroon, contentColor = Color.White,
        icon = { Icon(Icons.Filled.Add, null) }, text = { Text(label) },
        shape = RoundedCornerShape(18.dp)
    )
}

/** Gradient header used on every home screen. */
@Composable
fun HomeHeader(greeting: String, title: String, subtitle: String, unread: Int, onBell: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brush.linearGradient(listOf(Brand.Navy, Brand.NavyMid, Brand.NavyDark)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 26.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.logo_disc), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // his request (Nov 8, 10:15 PM): the full salaam; it may wrap to two lines on a 360dp phone
                    Text(greeting, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = onBell, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                        Icon(Icons.Outlined.Notifications, "الإشعارات", tint = Color.White)
                    }
                    if (unread > 0) Box(
                        Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Brand.Maroon),
                        contentAlignment = Alignment.Center
                    ) { Text("$unread", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StudentRow(s: Student, snap: CenterSnapshot, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val stats = remember(s, snap.recitations, snap.attendance) { Stats.forStudent(s, snap) }
    AppCard(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudentAvatar(s)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.fullName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (isPending(s.id)) { Spacer(Modifier.width(6.dp)); PendingChip() }
                }
                Text(
                    (snap.circle(s.circleId)?.name ?: "بدون حلقة") + " • " + Fmt.one(stats.juzEquivalent) + " جزء",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                LinearMeter(stats.goalProgress, Brand.Navy)
            }
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun RecordRow(r: Recitation, snap: CenterSnapshot, showStudent: Boolean, onDelete: (() -> Unit)? = null) {
    AppCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(kindColor(r.kind).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (r.kind) { RecordKind.HIFZ -> Icons.Outlined.AutoStories; RecordKind.TASMEE -> Icons.Outlined.RecordVoiceOver; RecordKind.MURAJA -> Icons.Outlined.Replay; RecordKind.AZFAR -> Icons.Outlined.Star; RecordKind.SARD -> Icons.Outlined.MenuBook },
                    null, tint = kindColor(r.kind)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (showStudent) (snap.student(r.studentId)?.fullName ?: "") else r.kind.label,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    Stats.describeRange(r) + " • " + Stats.ayatOf(r) + " آية",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Muted
                )
                Text(Fmt.relative(r.recordedAt) + (if (r.mistakes > 0) " • ${r.mistakes} أخطاء" else "") + (snap.recorder(r.byName, r.teacherId)?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
                if (isPending(r.id)) { Spacer(Modifier.height(4.dp)); PendingChip() }
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(r.grade.label, gradeColor(r.grade))
                if (showStudent) { Spacer(Modifier.height(4.dp)); Pill(r.kind.short, kindColor(r.kind)) }
                if (onDelete != null) {
                    var ask by remember { mutableStateOf(false) }
                    IconButton(onClick = { ask = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, "حذف", tint = Brand.Muted, modifier = Modifier.size(18.dp))
                    }
                    if (ask) ConfirmDialog("حذف السجل؟", "سيُحذف هذا السجل وتتحدث الإحصائيات.", "حذف", onDelete, { ask = false }, destructive = true)
                }
            }
        }
    }
}

@Composable
fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Brand.Navy, selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = singleLine, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
    )
}

/** Simple picker dialog for choosing one item from a list. */
@Composable
fun <T> PickerDialog(title: String, items: List<T>, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit, searchable: Boolean = false) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("بحث") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                }
                val shown = items.filter { q.isBlank() || label(it).contains(q.trim()) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown) { item ->
                        Text(
                            label(item), style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(item); onDismiss() }.padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun SelectorField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Brand.Line, RoundedCornerShape(14.dp))
            .background(Brand.Card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, tint = Brand.Muted)
        }
    }
}

/** v3.3: a teacher looking at another teacher's circle or student (view only). */
@Composable
fun ReadOnlyNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Visibility, null, tint = Brand.Navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Brand.Navy)
    }
}

/** v3.2: is this row (a record, student, attendance mark…) still waiting to reach the server? */
@Composable
fun isPending(key: String): Boolean = LocalContainer.current.center.pendingKeys.collectAsStateWithLifecycle().value.contains(key) || SyncSim.pendingKeys.contains(key)

/** v3.2: the small «بانتظار الإرسال» chip on a row saved on the phone and not yet on the server. */
@Composable
fun PendingChip(modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Brand.GoldSoft).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudUpload, null, tint = Brand.Gold, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("بانتظار الإرسال", style = MaterialTheme.typography.labelSmall, color = Brand.Ink)
    }
}

/** Debug-only (CI screenshots): a simulated outbox, so the status line and chips can be shown in the demo build. */
object SyncSim {
    var pending: Int? = null
    var needsPassword: Boolean = false
    var pendingKeys: Set<String> = emptySet()
}

/**
 * Demo notice, or — with the central database — v3.2's sync status line for whoever writes
 * («كل شي مرفوع ✅» / «٣ بانتظار الإرسال»), the password-again card when a login ended with
 * changes waiting, and the changes the server refused (kept, with the reason).
 */
@Composable
fun StatusBanners(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val user = container.auth.currentUser.collectAsStateWithLifecycle().value
    if (container.auth.mode == BackendMode.DEMO) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoBanner()
            SyncSim.pending?.let { SyncStatusLine(it, SyncSim.needsPassword, 0) }
            if (SyncSim.needsPassword) ReauthCard(com.alsiddiq.app.data.ReauthTarget("sim", user?.fullName ?: "المعلم", Role.TEACHER, SyncSim.pending ?: 0)) { _ -> Result.success(0) }
        }
        return
    }
    val info = container.center.outboxInfo.collectAsStateWithLifecycle().value
    val writer = user != null && user.role != Role.VIEWER
    if (!writer && info.pending == 0 && info.needsPasswordFor.isEmpty() && info.rejected.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncStatusLine(info.pending, info.needsPasswordFor.isNotEmpty(), info.rejected.size)
        info.needsPasswordFor.forEach { t -> ReauthCard(t) { pw -> container.center.reauthenticate(t.id, pw) } }
        if (info.rejected.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = true }) { Text("عرض ما رفضه الخادم (${info.rejected.size})", color = Brand.Red) }
            if (open) AlertDialog(onDismissRequest = { open = false },
                title = { Text("تعديلات رفضها الخادم") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { info.rejected.forEach { r ->
                    Text(r.label, style = MaterialTheme.typography.titleSmall); Text(r.reason, style = MaterialTheme.typography.bodySmall, color = Brand.Muted); Spacer(Modifier.height(8.dp)) } } },
                confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق") } },
                dismissButton = { TextButton(onClick = { scope.launch { container.center.dismissRejected(null) }; open = false }) { Text("مسحها", color = Brand.Red) } })
        }
    }
}

@Composable
fun SyncStatusLine(pending: Int, needsPassword: Boolean, rejected: Int) {
    val ok = pending == 0 && !needsPassword
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) Brand.GreenSoft else Brand.NavySoft).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (ok) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload, null, tint = if (ok) Brand.Green else Brand.Navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(com.alsiddiq.app.domain.SyncPolicy.statusLine(pending, needsPassword, rejected), style = MaterialTheme.typography.labelMedium, color = if (ok) Brand.Green else Brand.Navy, modifier = Modifier.weight(1f))
        if (!ok && !needsPassword) Text("يُرفع تلقائياً", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
    }
}

/** v3.2: the login that saved these changes ended — enter its password again; nothing is lost meanwhile. */
@Composable
fun ReauthCard(t: com.alsiddiq.app.data.ReauthTarget, onSubmit: suspend (String) -> Result<Int>) {
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft).padding(12.dp)) {
        Text("انتهت جلسة ${t.name}", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
        Text("${com.alsiddiq.app.domain.SyncPolicy.ar(t.waiting)} تعديل محفوظ على الجهاز بانتظار الإرسال. أدخل كلمة السر لرفعها، ولن يضيع شيء.", style = MaterialTheme.typography.bodySmall, color = Brand.Ink)
        Spacer(Modifier.height(8.dp))
        com.alsiddiq.app.ui.auth.PasswordField(pw, { pw = it; err = null }, "كلمة سر ${t.name}")
        err?.let { Text(it, color = Brand.Red, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(6.dp))
        Button(enabled = pw.isNotBlank() && !busy, onClick = {
            busy = true
            scope.launch { onSubmit(pw).onFailure { err = it.message }.onSuccess { pw = "" }; busy = false }
        }, shape = RoundedCornerShape(12.dp)) { Text(if (busy) "جارٍ الرفع…" else "أدخل وارفع") }
    }
}
