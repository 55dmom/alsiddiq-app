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
            .background(Brush.linearGradient(listOf(Brand.Navy, Color(0xFF3A3486), Brand.NavyDark)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 26.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
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
                Text(s.fullName, style = MaterialTheme.typography.titleSmall)
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
                    when (r.kind) { RecordKind.HIFZ -> Icons.Outlined.AutoStories; RecordKind.TASMEE -> Icons.Outlined.RecordVoiceOver; RecordKind.MURAJA -> Icons.Outlined.Replay },
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
            .background(Color.White).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, tint = Brand.Muted)
        }
    }
}

/** Demo notice, or — with the central database — how many saved changes are still waiting for a connection. */
@Composable
fun StatusBanners(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    if (container.auth.mode == BackendMode.DEMO) { Box(modifier) { DemoBanner() }; return }
    val sync = container.center.syncState.collectAsStateWithLifecycle().value
    if (sync is com.alsiddiq.app.data.SyncState.Pending) {
        Row(
            modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.NavySoft).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CloudUpload, null, tint = Brand.Navy, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${sync.count} تعديل محفوظ على الجهاز وسيُرفع تلقائياً عند توفر الإنترنت", style = MaterialTheme.typography.labelMedium, color = Brand.Navy)
        }
    }
}
