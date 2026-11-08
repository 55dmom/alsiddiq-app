package com.alsiddiq.app.ui.theme

import android.icu.util.IslamicCalendar
import java.util.Calendar

/**
 * «ثيمات المناسبات»: a theme that is FORCED while its occasion lasts and overrides the user's own choice.
 * When the dates pass, the user's own choice comes back by itself.
 *
 * To add one (his files): add its [AppPalette] (with `tools = ToolColors(...)` so his tools follow it too),
 * then ONE entry to [Occasions.all] with a Hijri range (Umm al-Qura calendar), a Gregorian range, or both.
 * Ranges repeat every year; a range may cross the year end (e.g. 12/25 → 1/2). Either range being active is enough.
 */
data class DayRange(val fromMonth: Int, val fromDay: Int, val toMonth: Int, val toDay: Int) {
    fun contains(month: Int, day: Int): Boolean {
        val x = month * 100 + day; val a = fromMonth * 100 + fromDay; val b = toMonth * 100 + toDay
        return if (a <= b) x in a..b else (x >= a || x <= b)
    }
}

data class Occasion(
    val id: String,
    val name: String,
    val palette: AppPalette,
    /** Hijri months 1..12 (محرم = 1 … ذو الحجة = 12), Umm al-Qura. */
    val hijri: DayRange? = null,
    /** Gregorian months 1..12. */
    val gregorian: DayRange? = null,
)

object Occasions {
    /** His occasion themes go here (none yet; waiting for his files). */
    val all: List<Occasion> = listOf()

    /** Debug/CI only: pretend this occasion id is active (for screenshots). */
    var forceForCi: String? = null

    fun activeNow(now: Long = System.currentTimeMillis()): Occasion? {
        forceForCi?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        if (all.isEmpty()) return null
        val g = Calendar.getInstance().apply { timeInMillis = now }
        val gm = g.get(Calendar.MONTH) + 1; val gd = g.get(Calendar.DAY_OF_MONTH)
        val (hm, hd) = runCatching {
            val h = IslamicCalendar().apply { calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA; timeInMillis = now }
            (h.get(android.icu.util.Calendar.MONTH) + 1) to h.get(android.icu.util.Calendar.DAY_OF_MONTH)
        }.getOrDefault(0 to 0)
        return all.firstOrNull { o ->
            (o.gregorian?.contains(gm, gd) == true) || (hm > 0 && o.hijri?.contains(hm, hd) == true)
        }
    }
}
