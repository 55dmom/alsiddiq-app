package com.alsiddiq.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val zone = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))
    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
    private val dayMonth = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar"))

    private val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    fun relative(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now())
        return when {
            days == 0L -> "اليوم ${time.format(d)}"
            days == 1L -> "أمس ${time.format(d)}"
            days < 7 -> "قبل $days أيام"
            else -> date.format(d)
        }
    }

    fun date(d: LocalDate): String = date.format(d)
    fun dayTitle(d: LocalDate): String = dayMonth.format(d)

    fun hijri(d: LocalDate = LocalDate.now()): String = try {
        val h = HijrahDate.from(d)
        "${h.get(ChronoField.DAY_OF_MONTH)} ${hijriMonths[h.get(ChronoField.MONTH_OF_YEAR) - 1]} ${h.get(ChronoField.YEAR)}هـ"
    } catch (e: Exception) { "" }

    fun one(x: Double): String = if (x >= 10 || x == Math.floor(x)) x.toInt().toString() else String.format(Locale.US, "%.1f", x)
    fun pct(x: Float): String = "${(x * 100).toInt()}%"
}
