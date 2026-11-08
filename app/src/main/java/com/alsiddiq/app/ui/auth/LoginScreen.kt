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
import com.alsiddiq.app.ui.components.DemoBanner
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.launch

/** One password field, nothing else. The password itself decides who you are. */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, onDone: (() -> Unit)? = null, modifier: Modifier = Modifier) {
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
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun GateFrame(onBack: (() -> Unit)? = null, tools: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val name by LocalContainer.current.auth.centerName.collectAsState()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyDark, Brand.Bg), endY = 1500f))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onBack != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع", tint = Color.White) }
                }
                Spacer(Modifier.height(4.dp))
            } else if (tools) {
                // «الأدوات»: his side buttons, open to everyone before any password.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    com.alsiddiq.app.ui.components.ToolsButton()
                    com.alsiddiq.app.ui.components.ThemesButton()
                }
                Spacer(Modifier.height(8.dp))
            } else Spacer(Modifier.height(52.dp))
            Box(Modifier.size(132.dp).clip(RoundedCornerShape(36.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.logo), "شعار مركز الصديق", Modifier.size(118.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(name.ifBlank { "مركز الصديق لتعليم القرآن الكريم" }, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Box(Modifier.padding(top = 8.dp).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brand.Gold))
            Spacer(Modifier.height(26.dp))
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
        Surface(shape = RoundedCornerShape(26.dp), color = Brand.Card, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("أدخل كلمة السر", style = MaterialTheme.typography.titleLarge)
                Text("لعرض الطلاب والنتائج والتقارير", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Spacer(Modifier.height(16.dp))
                PasswordField(pass, { pass = it; error = null }, "كلمة السر", onDone = { submit() })
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, color = Brand.Red, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("دخول", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text("كلمة السر تحصل عليها من إدارة المركز", style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        if (demo) {
            Spacer(Modifier.height(18.dp)); DemoBanner(); Spacer(Modifier.height(8.dp))
            Text("نسخة تجريبية: العرض ${DemoSeed.VIEWER_PASSWORD} • الإدارة ${DemoSeed.ADMIN_PASSWORD} • معلم ${DemoSeed.TEACHER_PASSWORD}",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted, textAlign = TextAlign.Center)
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
