package com.alsalmi.buttons.prayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Settings, saved in SharedPreferences "alsalmi_prayer" (also read by the alarm receiver). */
internal class PrSettings(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("alsalmi_prayer", Context.MODE_PRIVATE)
    var methodId by mutableStateOf(p.getString("method", "umm")!!)
    var hanafi by mutableStateOf(p.getBoolean("hanafi", false))
    var place by mutableStateOf(p.getString("place", "الطائف")!!)
    var lat by mutableStateOf(p.getFloat("lat", 21.2703f).toDouble())
    var lng by mutableStateOf(p.getFloat("lng", 40.4158f).toDouble())
    var tz by mutableStateOf(p.getString("tz", "Asia/Riyadh")!!)
    val iqama = mutableStateListOf<Int>().apply {
        val s = p.getString("iqama", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        addAll(if (s.size == 5) s else DEFAULT_IQAMA)
    }
    val notify = mutableStateListOf<String>().apply { addAll(p.getStringSet("notify", emptySet())!!) }

    val method get() = PrMethod.byId(methodId)
    val zone: TimeZone get() = TimeZone.getTimeZone(tz)

    fun save() {
        p.edit().putString("method", methodId).putBoolean("hanafi", hanafi).putString("place", place)
            .putFloat("lat", lat.toFloat()).putFloat("lng", lng.toFloat()).putString("tz", tz)
            .putString("iqama", iqama.joinToString(",")).putStringSet("notify", notify.toSet()).apply()
        PrAlarms.scheduleNext(ctx)
    }

    fun slots(at: Long): List<PrSlot> {
        val t = PrEngine.day(at, lat, lng, zone, method, hanafi)
        return PrPrayer.entries.mapNotNull { pr ->
            t[pr]?.let { ms -> PrSlot(pr, ms, iqIndex(pr)?.let { ms + iqama[it] * 60_000L }) }
        }
    }

    /** previous / next prayer around [now] (sunrise is a time, not a prayer). */
    fun around(now: Long): Pair<PrSlot?, PrSlot?> {
        val day = 86_400_000L
        val all = (slots(now - day) + slots(now) + slots(now + day)).filter { it.prayer.isPrayer }
        return all.lastOrNull { it.time <= now } to all.firstOrNull { it.time > now }
    }

    companion object {
        val DEFAULT_IQAMA = listOf(25, 20, 20, 10, 20)
        fun iqIndex(p: PrPrayer) = when (p) { PrPrayer.FAJR -> 0; PrPrayer.DHUHR -> 1; PrPrayer.ASR -> 2; PrPrayer.MAGHRIB -> 3; PrPrayer.ISHA -> 4; else -> null }
    }
}

internal data class PrSlot(val prayer: PrPrayer, val time: Long, val iqama: Long?)

internal object PrFmt {
    fun time(ms: Long, z: TimeZone): String {
        val f = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = z }
        val h = java.util.Calendar.getInstance(z).apply { timeInMillis = ms }.get(java.util.Calendar.HOUR_OF_DAY)
        return f.format(Date(ms)) + if (h < 12) " ص" else " م"
    }
    fun span(ms: Long, seconds: Boolean): String {
        val t = maxOf(0L, ms / 1000)
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        if (seconds) return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        return if (h > 0) "$h س $m د" else "$m دقيقة"
    }
    fun date(ms: Long, z: TimeZone): String {
        val days = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
        val months = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val c = java.util.Calendar.getInstance(z).apply { timeInMillis = ms }
        return "${days[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]} ${c.get(java.util.Calendar.DAY_OF_MONTH)} ${months[c.get(java.util.Calendar.MONTH)]} · ${PrEngine.hijri(ms, z)}"
    }
}

/** Local notifications with AlarmManager: one alarm for the next enabled prayer, re-armed after each one. */
internal object PrAlarms {
    private const val CHANNEL = "alsalmi_prayer"
    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 7001, Intent(ctx, PrayerAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val s = PrSettings(ctx)
        if (s.notify.isEmpty()) return
        val now = System.currentTimeMillis()
        val next = (0..2).flatMap { s.slots(now + it * 86_400_000L) }
            .firstOrNull { it.time > now + 1000 && it.prayer.name in s.notify } ?: return
        ctx.getSharedPreferences("alsalmi_prayer", Context.MODE_PRIVATE).edit()
            .putString("pending", next.prayer.name).putLong("pendingAt", next.time).apply()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time, pi)
    }

    fun post(ctx: Context) {
        val p = ctx.getSharedPreferences("alsalmi_prayer", Context.MODE_PRIVATE)
        val pr = p.getString("pending", null)?.let { n -> PrPrayer.entries.firstOrNull { it.name == n } } ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val s = PrSettings(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت صلاة ${pr.title}")
            .setContentText("${s.place} — ${PrFmt.time(p.getLong("pendingAt", System.currentTimeMillis()), s.zone)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(7000 + pr.ordinal, n)
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrAlarms.post(context)
        PrAlarms.scheduleNext(context)
    }
}

class PrayerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { PrAlarms.scheduleNext(context) }
}

/**
 * «مواقيت الصلاة» — the button's single entry composable.
 * @param onBack shows a back arrow in the top bar when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = remember { PrSettings(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPlaces by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var pendingNotify by remember { mutableStateOf<PrPrayer?>(null) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    fun useLocation(l: Location) {
        locating = false
        s.lat = l.latitude; s.lng = l.longitude; s.tz = TimeZone.getDefault().id
        val near = PrCity.all.map { c -> c to FloatArray(1).also { Location.distanceBetween(l.latitude, l.longitude, c.lat, c.lng, it) }[0] }.minByOrNull { it.second }
        s.place = if (near != null && near.second < 40_000f) "${near.first.name} (موقعي)" else "موقعي الحالي"
        s.save(); showPlaces = false
    }

    @Suppress("MissingPermission")
    fun locateNow() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000L) { useLocation(last); return }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) { locating = false; message = "خدمة الموقع مقفلة. افتحها أو اختر مدينتك من القائمة."; return }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { lm.removeUpdates(this); useLocation(location) }
            @Deprecated("old API") override fun onStatusChanged(p: String?, st: Int, b: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
            .onFailure { locating = false; message = "ما قدرنا نحدد موقعك الحين. اختر مدينتك من القائمة." }
    }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) locateNow() else { locating = false; message = "ما وصلنا إذن الموقع. اختر مدينتك من القائمة." }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pendingNotify
        if (ok && p != null) { s.notify.add(p.name); s.save() } else if (!ok) message = "التنبيهات مقفلة من إعدادات الجهاز."
        pendingNotify = null
    }
    fun locate() {
        locating = true; message = ""
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) locateNow() else locPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun toggleNotify(p: PrPrayer) {
        if (p.name in s.notify) { s.notify.remove(p.name); s.save(); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotify = p; notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else { s.notify.add(p.name); s.save() }
    }

    PrTheme {
        Scaffold(
            containerColor = PrC.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("مواقيت الصلاة", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                    actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات", tint = PrC.accent) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrC.bg),
                )
            },
        ) { pad ->
            val (prev, next) = s.around(now)
            val today = s.slots(now)
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PrHeroCard(now, prev, next, s.zone) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f, fill = false).clip(RoundedCornerShape(50)).background(PrC.soft).prPress { showPlaces = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = PrC.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.place, color = PrC.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = PrC.accent)
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(PrC.gradient).prPress { locate() }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (locating) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else PrGlyph("pin", Color.White, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("موقعي", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                if (message.isNotEmpty()) item { Text(message, color = PrC.warn, fontSize = 13.sp) }
                item {
                    PrCard(padding = 6.dp) {
                        Column {
                            today.forEach { sl ->
                                val isNext = next != null && next.prayer == sl.prayer && next.time == sl.time
                                PrRow(sl, isNext, sl.prayer.name in s.notify, s.zone) { toggleNotify(sl.prayer) }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "طريقة الحساب: ${s.method.name} · العصر: ${if (s.hanafi) "الحنفي" else "الجمهور (شافعي)"}\nالأوقات محسوبة على جهازك بدون إنترنت.",
                        fontSize = 12.sp, color = PrC.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 18.sp,
                    )
                }
            }
        }

        if (showPlaces) PrPlacesSheet(s, locating, onLocate = { locate() }, onDismiss = { showPlaces = false }, onError = { message = it })
        if (showSettings) PrSettingsSheet(s, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PrHeroCard(now: Long, prev: PrSlot?, next: PrSlot?, zone: TimeZone) {
    val shape = RoundedCornerShape(28.dp)
    val total = (next?.time ?: now) - (prev?.time ?: now)
    val done = now - (prev?.time ?: now)
    Box(Modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = PrC.accent.copy(alpha = 0.35f), spotColor = PrC.accent.copy(alpha = 0.35f)).clip(shape).background(PrC.gradient)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = 100.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, -20.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = 70.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("الصلاة القادمة", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    AnimatedContent(next?.prayer?.title ?: "—", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "next") {
                        Text(it, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(next?.let { PrFmt.time(it.time, zone) } ?: "", color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                PrRing(if (total > 0) done.toFloat() / total else 0f, size = 96.dp, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("بعد", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        Text(PrFmt.span((next?.time ?: now) - now, true), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrGlyph("clock", Color.White.copy(alpha = 0.9f), 16.dp)
                Spacer(Modifier.width(6.dp))
                if (prev != null) Text("السابقة: ${prev.prayer.title} — مضى ${PrFmt.span(now - prev.time, false)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                next?.iqama?.let {
                    Text("الإقامة ${PrFmt.time(it, zone)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Text(PrFmt.date(now, zone), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PrRow(sl: PrSlot, isNext: Boolean, on: Boolean, zone: TimeZone, onBell: () -> Unit) {
    val fg = if (isNext) Color.White else PrC.text
    val glyph = when (sl.prayer) { PrPrayer.FAJR -> "moon"; PrPrayer.SUNRISE -> "sunrise"; PrPrayer.DHUHR -> "sun"; PrPrayer.ASR -> "sun"; PrPrayer.MAGHRIB -> "sunrise"; PrPrayer.ISHA -> "moon" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).then(if (isNext) Modifier.background(PrC.gradient) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (isNext) Color.White.copy(alpha = 0.2f) else PrC.soft), contentAlignment = Alignment.Center) {
            PrGlyph(glyph, if (isNext) Color.White else PrC.accent, 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sl.prayer.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            sl.iqama?.let { Text("الإقامة ${PrFmt.time(it, zone)}", color = if (isNext) Color.White.copy(alpha = 0.85f) else PrC.text2, fontSize = 12.sp) }
        }
        Text(PrFmt.time(sl.time, zone), color = fg, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        if (sl.prayer.isPrayer) {
            IconButton(onClick = onBell, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Notifications, "تنبيه ${sl.prayer.title}",
                    tint = if (isNext) (if (on) Color.White else Color.White.copy(alpha = 0.45f)) else if (on) PrC.accent else PrC.line, modifier = Modifier.size(20.dp))
            }
        } else Spacer(Modifier.size(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrPlacesSheet(s: PrSettings, locating: Boolean, onLocate: () -> Unit, onDismiss: () -> Unit, onError: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var latT by remember { mutableStateOf("") }
    var lngT by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PrC.bg) {
        PrTheme {
            val xs = PrCity.all.filter { q.isBlank() || it.name.contains(q.trim()) || it.region.contains(q.trim()) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PrSearch(q, { q = it }, "ابحث عن مدينة أو دولة") }
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PrC.soft).prPress { onLocate() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrGlyph("pin", PrC.accent, 18.dp); Spacer(Modifier.width(8.dp))
                        Text(if (locating) "جاري تحديد موقعك…" else "استخدم موقعي (GPS)", color = PrC.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (xs.isEmpty()) item { PrEmpty("ما لقينا المدينة", "اكتب الإحداثيات تحت، أو استخدم موقعك.") { PrGlyph("pin", PrC.accent, 34.dp) } }
                PrCity.regions.forEach { g ->
                    val gx = xs.filter { it.region == g }
                    if (gx.isNotEmpty()) {
                        item { Text(g, color = PrC.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                        items(gx, key = { it.name }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PrC.card).clickable {
                                    s.place = c.name; s.lat = c.lat; s.lng = c.lng; s.tz = c.tz; s.save(); onDismiss()
                                }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.name, color = PrC.text, modifier = Modifier.weight(1f))
                                if (s.place == c.name) Icon(Icons.Filled.Check, null, tint = PrC.accent)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إحداثيات يدوية (بتوقيت جهازك)", color = PrC.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(latT, { latT = it }, label = { Text("خط العرض، مثل 21.27") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(lngT, { lngT = it }, label = { Text("خط الطول، مثل 40.42") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val la = latT.replace(',', '.').toDoubleOrNull(); val lo = lngT.replace(',', '.').toDoubleOrNull()
                            if (la == null || lo == null || kotlin.math.abs(la) > 66 || kotlin.math.abs(lo) > 180) onError("الإحداثيات غير صحيحة (خط العرض بين -66 و66)")
                            else {
                                s.lat = la; s.lng = lo; s.tz = TimeZone.getDefault().id
                                s.place = String.format(Locale.US, "%.3f، %.3f", la, lo); s.save(); onDismiss()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("اعتمد الإحداثيات") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrSettingsSheet(s: PrSettings, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { s.save(); onDismiss() }, containerColor = PrC.bg) {
        PrTheme {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("طريقة الحساب", fontWeight = FontWeight.SemiBold, color = PrC.text) }
                items(PrMethod.all, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (s.methodId == m.id) PrC.soft else PrC.card)
                            .clickable { s.methodId = m.id; s.save() }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.name, color = PrC.text, modifier = Modifier.weight(1f))
                        if (s.methodId == m.id) Icon(Icons.Filled.CheckCircle, null, tint = PrC.accent)
                    }
                }
                item { Text("أم القرى: الفجر 18.5°، والعشاء بعد المغرب بـ90 دقيقة (120 في رمضان).", fontSize = 12.sp, color = PrC.text2) }
                item {
                    Text("وقت العصر", fontWeight = FontWeight.SemiBold, color = PrC.text, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrChip("الجمهور", !s.hanafi, Modifier.weight(1f)) { s.hanafi = false; s.save() }
                        PrChip("الحنفي", s.hanafi, Modifier.weight(1f)) { s.hanafi = true; s.save() }
                    }
                }
                item { Text("الإقامة بعد الأذان (بالدقائق)", fontWeight = FontWeight.SemiBold, color = PrC.text, modifier = Modifier.padding(top = 6.dp)) }
                items(5) { i ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PrC.card).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")[i], color = PrC.text, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (s.iqama[i] > 0) { s.iqama[i] = s.iqama[i] - 1; s.save() } }) { Text("−", fontSize = 22.sp, color = PrC.accent) }
                        Text("${s.iqama[i]}", fontWeight = FontWeight.Bold, color = PrC.text, modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { if (s.iqama[i] < 60) { s.iqama[i] = s.iqama[i] + 1; s.save() } }) { Icon(Icons.Filled.Add, "زيادة", tint = PrC.accent) }
                    }
                }
                item {
                    TextButton(onClick = { PrSettings.DEFAULT_IQAMA.forEachIndexed { i, v -> s.iqama[i] = v }; s.save() }) { Text("رجّع الافتراضي") }
                }
            }
        }
    }
}

/** Ready-made full-screen Activity: `PrayerTimesActivity.start(context)`. */
class PrayerTimesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrayerTimesScreen(onBack = { finish() }) }
    }
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PrayerTimesActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
