@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alsiddiq.app.ui.about

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsiddiq.app.AlSiddiqApp
import com.alsiddiq.app.BuildConfig
import com.alsiddiq.app.data.AboutLocal
import com.alsiddiq.app.domain.AboutButton
import com.alsiddiq.app.domain.AboutPage
import com.alsiddiq.app.domain.AboutText
import com.alsiddiq.app.domain.Role
import com.alsiddiq.app.ui.LocalAppScope
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.LocalSnackbar
import com.alsiddiq.app.ui.components.AppCard
import com.alsiddiq.app.ui.components.ConfirmDialog
import com.alsiddiq.app.ui.components.PhotoUtil
import com.alsiddiq.app.ui.components.ScreenTopBar
import com.alsiddiq.app.ui.components.SectionHeader
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.theme.AlSiddiqTheme
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v3.4 «عن المركز»: open to everyone, no password. Photos (swipe when more than one), his text,
 * the address as its own card, then the contact buttons the admin adds. From the ADMIN account
 * the same page has «تعديل»: the text, the photos and the buttons (add, edit, reorder, delete).
 */
class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.alsiddiq.app.ui.theme.AppThemes.load(this)
        val container = (application as AlSiddiqApp).container
        val startEditing = BuildConfig.DEBUG && intent?.getBooleanExtra("edit", false) == true
        setContent {
            AlSiddiqTheme {
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar, LocalAppScope provides scope) {
                    Scaffold(containerColor = Brand.Bg, snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
                        Box(Modifier.padding(pad)) { AboutScreen(onBack = { finish() }, startEditing = startEditing) }
                    }
                }
            }
        }
    }
    companion object {
        fun start(ctx: Context, edit: Boolean = false) = ctx.startActivity(Intent(ctx, AboutActivity::class.java).putExtra("edit", edit).addFlags(if (ctx is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun aboutBitmap(path: String): ImageBitmap? {
    val repo = LocalContainer.current.center
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        val bytes = withContext(Dispatchers.IO) { runCatching { repo.aboutPhoto(path) }.getOrNull() }
        bmp = bytes?.let { b -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
    }
    return bmp
}

fun kindIcon(kind: String): ImageVector = when (kind) {
    "call" -> Icons.Outlined.Call
    "whatsapp" -> Icons.AutoMirrored.Outlined.Chat
    "email" -> Icons.Outlined.Email
    "map" -> Icons.Outlined.Place
    else -> Icons.Outlined.Link
}

fun openUri(ctx: Context, uri: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(ctx, "ما في تطبيق يفتح هذا الرابط", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
fun AboutScreen(onBack: () -> Unit, startEditing: Boolean = false) {
    val container = LocalContainer.current
    val repo = container.center
    val page by repo.about.collectAsStateWithLifecycle()
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val admin = user?.role == Role.ADMIN
    var editing by remember { mutableStateOf(startEditing && admin) }
    LaunchedEffect(admin) { if (startEditing && admin) editing = true; if (!admin) editing = false }
    LaunchedEffect(Unit) { repo.refreshAbout() }
    val ctx = LocalContext.current
    val bundled = remember { AboutLocal(ctx).bundledText() }
    val text = page.body ?: bundled
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ScreenTopBar(if (editing) "تعديل «عن المركز»" else "عن المركز", onBack = { if (editing) editing = false else onBack() }) {
            if (admin) TextButton(onClick = { editing = !editing }) { Text(if (editing) "تم" else "تعديل", color = Brand.Navy) }
        }
        if (editing) AboutEditor(page, text) else AboutView(page, text)
    }
}

@Composable
private fun AboutView(page: AboutPage, text: String) {
    val ctx = LocalContext.current
    val (body, address) = remember(text) { AboutText.split(text) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (page.photos.isNotEmpty()) item { PhotoGallery(page.photos) }
        item {
            AppCard {
                body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, line ->
                    val bullet = line.startsWith("•") || line.startsWith("*")
                    Text(line, style = if (i == 0) MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp) else MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                        color = if (i == 0) Brand.Navy else Brand.Ink, textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = if (bullet) 4.dp else 3.dp))
                }
            }
        }
        if (address != null) item {
            AppCard(onClick = { openUri(ctx, AboutText.uri("map", address)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brand.MaroonSoft), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Place, null, tint = Brand.Maroon) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("العنوان", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                        Text(address, style = MaterialTheme.typography.bodyLarge, color = Brand.Ink)
                    }
                    Icon(Icons.Outlined.Map, "الخريطة", tint = Brand.Maroon)
                }
            }
        }
        if (page.buttons.isNotEmpty()) {
            item { SectionHeader("تواصل معنا") }
            items(AboutLocal.sorted(page.buttons), key = { it.id }) { b -> ContactButton(b) { openUri(ctx, AboutText.uri(b.kind, b.value)) } }
        }
        if (!page.fromServer) item { Text("نسخة محفوظة على الجهاز", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun ContactButton(b: AboutButton, trailing: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Brand.Card, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.NavySoft), contentAlignment = Alignment.Center) { Icon(kindIcon(b.kind), null, tint = Brand.Navy) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.label, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                Text(AboutButton.kindName(b.kind) + " • " + b.value, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
            }
            trailing()
        }
    }
}

@Composable
private fun PhotoGallery(photos: List<String>) {
    val pager = rememberPagerState { photos.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(20.dp)).background(Brand.Card)) { i ->
            val b = aboutBitmap(photos[i])
            if (b != null) Image(b, "صورة المركز", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.Gold) }
        }
        if (photos.size > 1) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(photos.size) { i -> Box(Modifier.size(if (i == pager.currentPage) 9.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) Brand.Gold else Brand.Line)) }
        }
    }
}

/** Admin only: the text, the photos and the buttons. Each change is saved at once to the server (needs internet). */
@Composable
private fun AboutEditor(page: AboutPage, text: String) {
    val repo = LocalContainer.current.center
    val actions = rememberActions()
    val ctx = LocalContext.current
    var body by remember(text) { mutableStateOf(text) }
    var editBtn by remember { mutableStateOf<AboutButton?>(null) }
    var askDel by remember { mutableStateOf<AboutButton?>(null) }
    var askPhoto by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            actions.run("أُضيفت الصورة", {
                val bmp = withContext(Dispatchers.IO) { PhotoUtil.decode(ctx, uri) } ?: return@run Result.failure<Unit>(Exception("تعذّر قراءة الصورة"))
                val bytes = withContext(Dispatchers.Default) { aboutCompress(bmp) }
                repo.addAboutPhoto(bytes)
            }, onError = { busy = false }) { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("التعديلات تُحفظ على الخادم ويشوفها الجميع. تحتاج اتصالاً بالإنترنت.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
        item { SectionHeader("الصور") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                page.photos.forEach { p ->
                    Box(Modifier.size(86.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Card)) {
                        aboutBitmap(p)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        IconButton(onClick = { askPhoto = p }, modifier = Modifier.align(Alignment.TopStart).size(30.dp).padding(3.dp).clip(CircleShape).background(Brand.Card.copy(alpha = 0.85f))) {
                            Icon(Icons.Outlined.Close, "حذف الصورة", tint = Brand.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Surface(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy,
                    shape = RoundedCornerShape(14.dp), color = Brand.NavySoft, modifier = Modifier.size(86.dp)) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Brand.Navy)
                        Text("إضافة", style = MaterialTheme.typography.labelSmall, color = Brand.Navy)
                    }
                }
            }
        }
        item { SectionHeader("النص") }
        item {
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 8, shape = RoundedCornerShape(14.dp),
                label = { Text("نص «عن المركز» (اكتب العنوان بعد «العنوان:» ليظهر في بطاقة)") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { actions.run("حُفظ النص", { repo.saveAboutText(body) }) }, enabled = body.isNotBlank() && body.trim() != text.trim(),
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("حفظ النص") }
        }
        item { SectionHeader("أزرار التواصل", "زر جديد") { editBtn = AboutButton("", "", "call", "") } }
        if (page.buttons.isEmpty()) item { Text("لا توجد أزرار بعد. أضف أرقام الإدارة أو واتساب أو رابط أو إيميل أو الموقع.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
        val sorted = AboutLocal.sorted(page.buttons)
        items(sorted, key = { it.id }) { b ->
            val i = sorted.indexOf(b)
            ContactButton(b, trailing = {
                IconButton(onClick = { actions.run(null, { repo.moveAboutButton(b.id, true) }) }, enabled = i > 0, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "لأعلى") }
                IconButton(onClick = { actions.run(null, { repo.moveAboutButton(b.id, false) }) }, enabled = i < sorted.size - 1, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "لأسفل") }
                IconButton(onClick = { askDel = b }, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = Brand.Red) }
            }) { editBtn = b }
        }
    }
    editBtn?.let { b ->
        var label by remember(b) { mutableStateOf(b.label) }
        var kind by remember(b) { mutableStateOf(b.kind) }
        var value by remember(b) { mutableStateOf(b.value) }
        AlertDialog(onDismissRequest = { editBtn = null },
            title = { Text(if (b.id.isEmpty()) "زر جديد" else "تعديل الزر") },
            text = {
                Column {
                    OutlinedTextField(label, { label = it }, label = { Text("اسم الزر (مثال: جوال الإدارة)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("النوع", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AboutButton.KINDS.forEach { (k, n) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(n) }, leadingIcon = { Icon(kindIcon(k), null, Modifier.size(16.dp)) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value, { value = it }, singleLine = kind != "map", modifier = Modifier.fillMaxWidth(), label = { Text(when (kind) {
                        "call", "whatsapp" -> "الرقم مع رمز الدولة (مثال: +967…)"; "email" -> "الإيميل"; "map" -> "رابط الموقع أو العنوان"; else -> "الرابط" }) })
                }
            },
            confirmButton = { Button(enabled = label.isNotBlank() && value.isNotBlank(), onClick = {
                actions.run("حُفظ الزر", { repo.saveAboutButton(b.copy(label = label, kind = kind, value = value)) }) { editBtn = null } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editBtn = null }) { Text("إلغاء") } })
    }
    askDel?.let { b -> ConfirmDialog("حذف «${b.label}»؟", "يختفي الزر من صفحة «عن المركز» عند الجميع.", "حذف", { actions.run("حُذف الزر", { repo.deleteAboutButton(b.id) }) { askDel = null } }, { askDel = null }, destructive = true) }
    askPhoto?.let { p -> ConfirmDialog("حذف الصورة؟", "تختفي من صفحة «عن المركز» عند الجميع.", "حذف", { actions.run("حُذفت الصورة", { repo.removeAboutPhoto(p) }) { askPhoto = null } }, { askPhoto = null }, destructive = true) }
}

/** About photos are shown large: up to 1280 px, JPEG under ~400 KB. */
private fun aboutCompress(src: android.graphics.Bitmap): ByteArray {
    val scale = minOf(1f, 1280f / maxOf(src.width, src.height))
    val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
    var q = 85
    while (true) {
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, out)
        if (out.size() <= 400_000 || q <= 40) return out.toByteArray()
        q -= 10
    }
}
