package com.alsalmi.buttons.tasbeeh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «المسبحة» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbeehScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { TasbeehState(ctx) }
    val view = LocalView.current
    var customOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }

    TsbTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("المسبحة", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TsbCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.phrase.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 38.sp)
                        Spacer(Modifier.size(6.dp))
                        Text(s.phrase.ref, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                CounterRing(
                    count = s.count, target = s.target, progress = s.progress,
                    onTap = {
                        val reached = s.tap()
                        if (s.haptics) { if (reached) TsbHaptics.success(view) else TsbHaptics.tap(view) }
                    },
                )

                TsbCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الهدف", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TasbeehState.presetTargets.forEach { t ->
                                Chip("$t", s.target == t, Modifier.weight(1f)) { s.chooseTarget(t) }
                            }
                            Chip(if (s.isCustomTarget) "${s.target}" else "مخصص", s.isCustomTarget, Modifier.weight(1f)) { customOpen = true }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("الجولات", s.rounds, Modifier.weight(1f))
                    Stat("المجموع", s.total, Modifier.weight(1f))
                    Surface(
                        onClick = { resetOpen = true },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تصفير", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                TsbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الاهتزاز عند كل ضغطة", Modifier.weight(1f))
                        Switch(checked = s.haptics, onCheckedChange = { s.toggleHaptics() })
                    }
                }

                TsbCard {
                    Column {
                        Text("الأذكار", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                        TasbeehState.phrases.forEachIndexed { i, p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { s.select(p.id) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (s.selected == p.id) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                else Box(Modifier.size(24.dp).padding(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.text, fontSize = 17.sp, lineHeight = 28.sp)
                                    Text(p.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (s.counts[p.id] > 0) Text("${s.counts[p.id]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (i != TasbeehState.phrases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }

        if (customOpen) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { customOpen = false },
                title = { Text("عدد مخصص") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                        placeholder = { Text("مثال: 1000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        text.map { Character.getNumericValue(it) }.joinToString("").toIntOrNull()?.let { s.chooseTarget(it) }
                        customOpen = false
                    }) { Text("حفظ") }
                },
                dismissButton = { TextButton(onClick = { customOpen = false }) { Text("إلغاء") } },
            )
        }
        if (resetOpen) {
            AlertDialog(
                onDismissRequest = { resetOpen = false },
                title = { Text("تصفير العداد") },
                text = { Text("تبي تصفّر هذا الذكر بس، أو كل العدادات والمجموع؟") },
                confirmButton = { TextButton(onClick = { s.resetCurrent(); resetOpen = false }) { Text("هذا الذكر") } },
                dismissButton = { TextButton(onClick = { s.resetAll(); resetOpen = false }) { Text("الكل") } },
            )
        }
    }
}

@Composable
private fun CounterRing(count: Int, target: Int, progress: Float, onTap: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(270.dp).scale(if (pressed) 0.96f else 1f).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = src, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val w = 14.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(w))
            drawArc(accent, -90f, 360f * progress, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontSize = 68.sp, fontWeight = FontWeight.Bold)
            Text("من $target", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("اضغط للتسبيح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(title: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(title: String, value: Int, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.heightIn(min = 64.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ready-made full-screen Activity: `TasbeehActivity.start(context)`. */
class TasbeehActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TasbeehScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, TasbeehActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
