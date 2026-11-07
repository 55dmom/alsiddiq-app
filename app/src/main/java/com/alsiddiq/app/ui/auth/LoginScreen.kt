package com.alsiddiq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.components.DemoBanner
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val demo = container.auth.mode == BackendMode.DEMO

    fun submit() {
        if (phone.isBlank() || pass.isBlank()) { error = "أدخل رقم الجوال وكلمة المرور"; return }
        busy = true; error = null
        scope.launch {
            container.auth.signIn(phone, pass).onFailure { error = it.message ?: "تعذّر تسجيل الدخول" }
            busy = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyDark, Brand.Bg), endY = 1400f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(132.dp).clip(RoundedCornerShape(36.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Image(painterResource(R.drawable.logo), "شعار مركز الصديق", Modifier.size(118.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("مركز الصديق", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("لتعليم القرآن الكريم", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(28.dp))

            Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("تسجيل الدخول", style = MaterialTheme.typography.titleLarge)
                    Text("أدخل رقم جوالك وكلمة المرور التي زوّدتك بها إدارة المركز", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it; error = null },
                        label = { Text("رقم الجوال") },
                        leadingIcon = { Icon(Icons.Outlined.Phone, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it; error = null },
                        label = { Text("كلمة المرور") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { show = !show }) {
                                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "إخفاء" else "إظهار")
                            }
                        },
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { submit() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("دخول", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "نسيت كلمة المرور؟ تواصل مع إدارة المركز لإعادة تعيينها",
                        style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (demo) {
                Spacer(Modifier.height(18.dp))
                DemoBanner()
                Spacer(Modifier.height(10.dp))
                Text("جرّب بحساب تجريبي (كلمة المرور ${DemoSeed.DEMO_PASSWORD})", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(8.dp))
                val demos = listOf(
                    "الإدارة" to DemoSeed.ADMIN_PHONE, "معلم" to DemoSeed.TEACHER_PHONE,
                    "طالب" to DemoSeed.STUDENT_PHONE, "ولي أمر" to DemoSeed.PARENT_PHONE
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    demos.forEach { (label, p) ->
                        AssistChip(onClick = { phone = p; pass = DemoSeed.DEMO_PASSWORD; submit() }, label = { Text(label) })
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
