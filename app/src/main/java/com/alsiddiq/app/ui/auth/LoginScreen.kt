package com.alsiddiq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CoPresent
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alsiddiq.app.R
import com.alsiddiq.app.data.BackendMode
import com.alsiddiq.app.data.demo.DemoSeed
import com.alsiddiq.app.domain.Role
import com.alsiddiq.app.ui.LocalContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alsiddiq.app.ui.components.islamicPatternOverlay
import com.alsiddiq.app.ui.components.DemoBanner
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.launch

/** One password field, nothing else. The password itself decides who you are. */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, onDone: (() -> Unit)? = null, modifier: Modifier = Modifier, glass: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "إخفاء" else "إظهار")
            }
        },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = if (!glass) OutlinedTextFieldDefaults.colors() else OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFF3DD9A),
            focusedBorderColor = Color(0xFFD4AF5A), unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
            focusedLabelColor = Color(0xFFF3DD9A), unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
            focusedLeadingIconColor = Color(0xFFF3DD9A), unfocusedLeadingIconColor = Color.White.copy(alpha = 0.75f),
            focusedTrailingIconColor = Color.White, unfocusedTrailingIconColor = Color.White.copy(alpha = 0.75f))
    )
}

/** His request (Nov 8, 4:38 PM): «صفحة تسجيل الدخول الأولى خليها فخمة مو تقليدية». */
private val Amiri = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.amiri_bold, FontWeight.Bold))
private val GateGold = listOf(Color(0xFFF3DD9A), Color(0xFFD4AF5A), Color(0xFFB08A34))
private val GateNight = Color(0xFF05070C)

/** A thin gold ornament: line — diamond — line. */
@Composable
private fun GoldDivider(width: androidx.compose.ui.unit.Dp = 150.dp) {
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, GateGold[1]))))
        Box(Modifier.padding(horizontal = 6.dp).size(7.dp).graphicsLayer { rotationZ = 45f }.background(GateGold[1]))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(GateGold[1], Color.Transparent))))
    }
}

@Composable
private fun GateFrame(onBack: (() -> Unit)? = null, tools: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val name by LocalContainer.current.auth.centerName.collectAsState()
    // a soft entrance (fade + slide up, about 400 ms)
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.tween(420)) }
    Box(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Brand.NavyDark, lerp(Brand.NavyDark, GateNight, 0.65f), GateNight)))
        .islamicPatternOverlay(GateGold[1], 0.07f)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).imePadding()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onBack != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع", tint = Color.White) }
                }
                Spacer(Modifier.height(4.dp))
            } else Spacer(Modifier.height(52.dp))   // his request (Nov 8, 9:42 PM): no top buttons here any more («الأدوات» is under the card)
            // the logo in a soft gold ring with a glow
            Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(GateGold[1].copy(alpha = 0.32f), Color.Transparent))))
                Box(Modifier.size(140.dp).clip(CircleShape).background(Brush.sweepGradient(GateGold + GateGold.first())).padding(3.dp)
                    .clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    // the logo as a round white disc (logo_disc: his logo, scaled to fit inside the circle with padding), wholly inside the gold ring
                    Image(painterResource(R.drawable.logo_disc), "شعار مركز الصديق", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            Spacer(Modifier.height(10.dp))
            GoldDivider()
            Spacer(Modifier.height(8.dp))
            Text(name.ifBlank { "مركز الصديق لتعليم القرآن الكريم" }, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 46.sp,
                color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            GoldDivider(110.dp)
            Spacer(Modifier.height(24.dp))
            com.alsiddiq.app.ui.components.OccasionBanner(Modifier.padding(bottom = 14.dp))
            content()
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** The opening screen: a single password field. */
@Composable
fun LoginScreen() {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val demo = container.auth.mode == BackendMode.DEMO
    var needsSetup by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { if (!demo) needsSetup = container.auth.needsSetup() }
    if (needsSetup == true) { SetupScreen { needsSetup = false }; return }

    fun submit() {
        if (pass.isBlank()) { error = "اكتب كلمة السر"; return }
        busy = true; error = null
        scope.launch {
            container.auth.signIn(pass, null).onFailure { error = it.message ?: "تعذّر الدخول" }
            busy = false
        }
    }
    GateFrame(tools = true) {
        // a frosted-glass card with a gold hairline border, and a gold login button
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Brush.verticalGradient(listOf(GateGold[0].copy(alpha = 0.85f), GateGold[2].copy(alpha = 0.45f)))),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent))).padding(20.dp)) {
                Text("أدخل كلمة السر", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("لعرض الطلاب والنتائج والتقارير", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.height(16.dp))
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    PasswordField(pass, { pass = it; error = null }, "كلمة السر", onDone = { submit() }, glass = true)
                }
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(GateGold)),
                    shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF2A1E05), disabledContainerColor = Color.Transparent)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF2A1E05), strokeWidth = 2.dp)
                    else Text("دخول", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text("كلمة السر تحصل عليها من إدارة المركز", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        // v3.4: open to everyone before any password: «عن المركز» and his «تواصل مع المطوّر».
        Spacer(Modifier.height(12.dp))
        val gateCtx = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = { com.alsiddiq.app.ui.about.AboutActivity.start(gateCtx) }, shape = RoundedCornerShape(50), color = Brand.Card, shadowElevation = 2.dp, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Info, null, tint = Brand.Navy, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("عن المركز", color = Brand.Navy, style = MaterialTheme.typography.labelLarge)
                }
            }
            Surface(onClick = { com.alsalmi.buttons.contact.ContactDeveloperActivity.start(gateCtx) }, shape = RoundedCornerShape(50), color = Brand.Card, shadowElevation = 2.dp, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = Color(0xFF29A1DE), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("المطوّر", color = Brand.Navy, style = MaterialTheme.typography.labelLarge)   // opens «تواصل مع المطوّر»
                }
            }
        }
        // v3.9: «تواصل مع المركز» — only when the admin switched it on (default off); opens «عن المركز».
        val gateContact = com.alsiddiq.app.ui.LocalContainer.current.center.about.collectAsState().value.contactButton
        if (gateContact) {
            Spacer(Modifier.height(10.dp))
            Surface(onClick = { com.alsiddiq.app.ui.about.AboutActivity.start(gateCtx) }, shape = RoundedCornerShape(50), color = Brand.Card, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Call, null, tint = Brand.Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("تواصل مع المركز", color = Brand.Navy, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // His request (Nov 8): before any password, «مواقيت الصلاة» open and the tools under it (the same block as the «الأدوات» tab).
        Spacer(Modifier.height(18.dp))
        com.alsiddiq.app.ui.components.ToolsBlock(titleColor = Color.White)
        if (demo) {
            Spacer(Modifier.height(18.dp)); DemoBanner(); Spacer(Modifier.height(8.dp))
            Text("نسخة تجريبية: العرض ${DemoSeed.VIEWER_PASSWORD} • الإدارة ${DemoSeed.ADMIN_PASSWORD} • معلم ${DemoSeed.TEACHER_PASSWORD}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

/** Settings → «الإدارة» or «المعلمين»: that section's own password. */
@Composable
fun RoleLoginScreen(kind: Role, onBack: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val admin = kind == Role.ADMIN
    fun submit() {
        if (pass.isBlank()) { error = "اكتب كلمة السر"; return }
        busy = true; error = null
        scope.launch {
            container.auth.signIn(pass, kind).onFailure { error = it.message ?: "تعذّر الدخول" }
            busy = false
        }
    }
    GateFrame(onBack = onBack) {
        Surface(shape = RoundedCornerShape(26.dp), color = Brand.Card, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (admin) Brand.NavySoft else Brand.MaroonSoft), contentAlignment = Alignment.Center) {
                        Icon(if (admin) Icons.Outlined.AdminPanelSettings else Icons.Outlined.CoPresent, null, tint = if (admin) Brand.Navy else Brand.Maroon)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (admin) "دخول الإدارة" else "دخول المعلمين", style = MaterialTheme.typography.titleLarge)
                        Text(if (admin) "كلمة سر الإدارة" else "كلمة سرك الخاصة (من الإدارة): تعدّل حلقتك وتشاهد البقية", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PasswordField(pass, { pass = it; error = null }, if (admin) "كلمة سر الإدارة" else "كلمة سر المعلم", onDone = { submit() })
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, color = Brand.Red, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (admin) Brand.Navy else Brand.Maroon)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("دخول", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** First run only: centre name, the admin password and the general password. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val known by container.auth.centerName.collectAsState()
    var center by remember(known) { mutableStateOf(known.ifBlank { "مركز الصديق لتعليم القرآن الكريم" }) }
    var a1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        error = when {
            center.isBlank() -> "اكتب اسم المركز"
            a1.length < 6 || v1.length < 6 -> "كلمة السر 6 أحرف أو أرقام على الأقل"
            a1 != a2 -> "تأكيد كلمة سر الإدارة غير مطابق"
            v1 != v2 -> "تأكيد كلمة السر العامة غير مطابق"
            a1 == v1 -> "كلمة سر الإدارة يجب أن تختلف عن كلمة السر العامة"
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            container.auth.setup(center, a1, v1).onSuccess { onDone() }.onFailure { error = it.message ?: "تعذّر الإعداد" }
            busy = false
        }
    }
    GateFrame {
        Surface(shape = RoundedCornerShape(26.dp), color = Brand.Card, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("إعداد المركز لأول مرة", style = MaterialTheme.typography.titleLarge)
                Text("مرة واحدة فقط. كلمات السر تُحفظ مشفّرة على الخادم ولا يراها أحد.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(center, { center = it; error = null }, label = { Text("اسم المركز") }, leadingIcon = { Icon(Icons.Outlined.Mosque, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(14.dp))
                Text("كلمة سر الإدارة (تحكم كامل)", style = MaterialTheme.typography.titleSmall, color = Brand.Navy)
                Spacer(Modifier.height(6.dp))
                PasswordField(a1, { a1 = it; error = null }, "كلمة سر الإدارة")
                Spacer(Modifier.height(8.dp))
                PasswordField(a2, { a2 = it; error = null }, "تأكيد كلمة سر الإدارة")
                Spacer(Modifier.height(14.dp))
                Text("كلمة السر العامة (عرض فقط)", style = MaterialTheme.typography.titleSmall, color = Brand.Maroon)
                Text("يدخل بها الجميع لمشاهدة الطلاب والنتائج والتقارير بدون تعديل", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(6.dp))
                PasswordField(v1, { v1 = it; error = null }, "كلمة السر العامة")
                Spacer(Modifier.height(8.dp))
                PasswordField(v2, { v2 = it; error = null }, "تأكيد كلمة السر العامة", onDone = { submit() })
                Spacer(Modifier.height(10.dp))
                Text("كلمات سر المعلمين: لكل معلم كلمة سر خاصة تضيفها الإدارة لاحقاً من الإعدادات ← الإدارة ← المعلمون.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, color = Brand.Red, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("حفظ وبدء الاستخدام", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
