package com.alsiddiq.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alsiddiq.app.domain.Student
import com.alsiddiq.app.ui.LocalContainer
import com.alsiddiq.app.ui.rememberActions
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** v3.1: decoded student photos kept in memory while the app runs. */
object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(80)
    fun get(path: String?): ImageBitmap? = path?.let { cache.get(it) }
    fun put(path: String, b: ImageBitmap) { cache.put(path, b) }
}

/** Shrinks a photo to about 512 px (longest side) and JPEG under ~150 KB. */
object PhotoUtil {
    const val MAX_SIDE = 512
    const val MAX_BYTES = 150_000

    fun compress(src: Bitmap): ByteArray {
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        var q = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_BYTES || q <= 35) return bytes
            q -= 10
        }
    }

    /** Reads a picked/taken image (orientation applied on Android 9+), already reduced near the target size. */
    fun decode(ctx: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val s = info.size; val f = minOf(1f, 1024f / maxOf(s.width, s.height))
                dec.setTargetSize((s.width * f).toInt().coerceAtLeast(1), (s.height * f).toInt().coerceAtLeast(1))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    }.getOrNull()
}

/** Round avatar: the student's photo when there is one, otherwise the initial letters. */
@Composable
fun StudentAvatar(s: Student, size: Dp = 44.dp, color: Color = Brand.Navy) {
    val repo = LocalContainer.current.center
    val path = s.photoPath
    var bmp by remember(path) { mutableStateOf(PhotoCache.get(path)) }
    LaunchedEffect(path) {
        if (path != null && bmp == null) {
            val bytes = withContext(Dispatchers.IO) { runCatching { repo.studentPhoto(path) }.getOrNull() }
            bmp = bytes?.let { b -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
                ?.also { PhotoCache.put(path, it) }
        }
    }
    val b = bmp
    if (b != null) Image(b, s.fullName, Modifier.size(size).clip(CircleShape).border(1.5.dp, Brand.Gold.copy(alpha = 0.6f), CircleShape), contentScale = ContentScale.Crop)
    else Avatar(s.fullName, size, color)
}

/** Student page header: big photo, and for admin/teachers: gallery, camera, remove. */
@Composable
fun StudentPhotoCard(s: Student, canEdit: Boolean) {
    val ctx = LocalContext.current
    val repo = LocalContainer.current.center
    val actions = rememberActions()
    var busy by remember { mutableStateOf(false) }
    var askRemove by remember { mutableStateOf(false) }
    fun upload(bitmap: Bitmap?) {
        if (bitmap == null) return
        busy = true
        actions.run("تم حفظ الصورة", {
            val bytes = withContext(Dispatchers.Default) { PhotoUtil.compress(bitmap) }
            repo.setStudentPhoto(s.id, bytes)
        }, onError = { busy = false }) { busy = false }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) upload(PhotoUtil.decode(ctx, uri))
    }
    val shotFile = remember { File(File(ctx.cacheDir, "photos_tmp").apply { mkdirs() }, "camera.jpg") }
    val shotUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".photos", shotFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) upload(PhotoUtil.decode(ctx, shotUri))
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudentAvatar(s, 84.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.fullName, style = MaterialTheme.typography.titleMedium, color = Brand.Navy)
                Text(if (s.photoPath != null) "صورة الطالب" else "لا توجد صورة", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                if (com.alsiddiq.app.ui.screens.isPending("photo:${s.id}")) { Spacer(Modifier.height(4.dp)); com.alsiddiq.app.ui.screens.PendingChip() }
                if (canEdit) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(enabled = !busy, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            label = { Text("المعرض") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) })
                        AssistChip(enabled = !busy, onClick = { runCatching { camera.launch(shotUri) } },
                            label = { Text("الكاميرا") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)) })
                        if (s.photoPath != null) IconButton(enabled = !busy, onClick = { askRemove = true }) {
                            Icon(Icons.Outlined.DeleteOutline, "حذف الصورة", tint = Brand.Red)
                        }
                    }
                }
            }
        }
    }
    if (askRemove) AlertDialog(onDismissRequest = { askRemove = false },
        title = { Text("حذف صورة ${s.fullName}؟") },
        confirmButton = { TextButton(onClick = { askRemove = false; actions.run("تم حذف الصورة", { repo.setStudentPhoto(s.id, null) }) { } }) { Text("حذف", color = Brand.Red) } },
        dismissButton = { TextButton(onClick = { askRemove = false }) { Text("إلغاء") } })
}
