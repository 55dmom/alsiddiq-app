package com.alsalmi.buttons.prayer

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Calculation method: Fajr angle, Isha angle (or minutes after Maghrib). */
internal data class PrMethod(val id: String, val name: String, val fajr: Double, val isha: Double?, val ishaMinutes: Double) {
    companion object {
        val all = listOf(
            PrMethod("umm", "أم القرى (مكة)", 18.5, null, 90.0),
            PrMethod("mwl", "رابطة العالم الإسلامي", 18.0, 17.0, 0.0),
            PrMethod("egypt", "الهيئة المصرية العامة للمساحة", 19.5, 17.5, 0.0),
            PrMethod("karachi", "جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0, 0.0),
            PrMethod("isna", "أمريكا الشمالية (ISNA)", 15.0, 15.0, 0.0),
            PrMethod("kuwait", "الكويت", 18.0, 17.5, 0.0),
            PrMethod("qatar", "قطر", 18.0, null, 90.0),
            PrMethod("dubai", "دبي", 18.2, 18.2, 0.0),
        )
        fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
    }
}

internal data class PrCity(val name: String, val lat: Double, val lng: Double, val tz: String, val region: String) {
    companion object {
        val regions = listOf("السعودية", "اليمن", "الدول العربية", "العالم")
        val all = listOf(
            PrCity("الطائف", 21.2703, 40.4158, "Asia/Riyadh", "السعودية"),
            PrCity("مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh", "السعودية"),
            PrCity("المدينة المنورة", 24.4672, 39.6111, "Asia/Riyadh", "السعودية"),
            PrCity("الرياض", 24.7136, 46.6753, "Asia/Riyadh", "السعودية"),
            PrCity("جدة", 21.4858, 39.1925, "Asia/Riyadh", "السعودية"),
            PrCity("الدمام", 26.4207, 50.0888, "Asia/Riyadh", "السعودية"),
            PrCity("الخبر", 26.2172, 50.1971, "Asia/Riyadh", "السعودية"),
            PrCity("الظهران", 26.2361, 50.0393, "Asia/Riyadh", "السعودية"),
            PrCity("الجبيل", 27.0046, 49.646, "Asia/Riyadh", "السعودية"),
            PrCity("القطيف", 26.5196, 50.0115, "Asia/Riyadh", "السعودية"),
            PrCity("الأحساء (الهفوف)", 25.3833, 49.5833, "Asia/Riyadh", "السعودية"),
            PrCity("حفر الباطن", 28.4328, 45.9708, "Asia/Riyadh", "السعودية"),
            PrCity("أبها", 18.2164, 42.5053, "Asia/Riyadh", "السعودية"),
            PrCity("خميس مشيط", 18.3, 42.7333, "Asia/Riyadh", "السعودية"),
            PrCity("بيشة", 20.0005, 42.6052, "Asia/Riyadh", "السعودية"),
            PrCity("النماص", 19.1167, 42.1333, "Asia/Riyadh", "السعودية"),
            PrCity("محايل عسير", 18.55, 42.05, "Asia/Riyadh", "السعودية"),
            PrCity("الباحة", 20.0129, 41.4677, "Asia/Riyadh", "السعودية"),
            PrCity("بلجرشي", 19.859, 41.559, "Asia/Riyadh", "السعودية"),
            PrCity("تبوك", 28.3838, 36.555, "Asia/Riyadh", "السعودية"),
            PrCity("ضباء", 27.351, 35.69, "Asia/Riyadh", "السعودية"),
            PrCity("الوجه", 26.2333, 36.4667, "Asia/Riyadh", "السعودية"),
            PrCity("أملج", 25.0213, 37.2685, "Asia/Riyadh", "السعودية"),
            PrCity("بريدة", 26.3592, 43.9818, "Asia/Riyadh", "السعودية"),
            PrCity("عنيزة", 26.0843, 43.9935, "Asia/Riyadh", "السعودية"),
            PrCity("الرس", 25.8694, 43.4973, "Asia/Riyadh", "السعودية"),
            PrCity("حائل", 27.5114, 41.7208, "Asia/Riyadh", "السعودية"),
            PrCity("جازان", 16.8892, 42.5511, "Asia/Riyadh", "السعودية"),
            PrCity("صبيا", 17.1495, 42.6254, "Asia/Riyadh", "السعودية"),
            PrCity("أبو عريش", 16.9689, 42.8325, "Asia/Riyadh", "السعودية"),
            PrCity("صامطة", 16.596, 42.944, "Asia/Riyadh", "السعودية"),
            PrCity("نجران", 17.5656, 44.2289, "Asia/Riyadh", "السعودية"),
            PrCity("شرورة", 17.4667, 47.1167, "Asia/Riyadh", "السعودية"),
            PrCity("ينبع", 24.0895, 38.0618, "Asia/Riyadh", "السعودية"),
            PrCity("العلا", 26.6084, 37.923, "Asia/Riyadh", "السعودية"),
            PrCity("رابغ", 22.7986, 39.0349, "Asia/Riyadh", "السعودية"),
            PrCity("الليث", 20.15, 40.2667, "Asia/Riyadh", "السعودية"),
            PrCity("القنفذة", 19.1264, 41.0789, "Asia/Riyadh", "السعودية"),
            PrCity("عرعر", 30.9753, 41.0381, "Asia/Riyadh", "السعودية"),
            PrCity("سكاكا", 29.9697, 40.2064, "Asia/Riyadh", "السعودية"),
            PrCity("القريات", 31.3318, 37.3428, "Asia/Riyadh", "السعودية"),
            PrCity("طريف", 31.6725, 38.6637, "Asia/Riyadh", "السعودية"),
            PrCity("الخرج", 24.1556, 47.312, "Asia/Riyadh", "السعودية"),
            PrCity("الدوادمي", 24.5077, 44.3924, "Asia/Riyadh", "السعودية"),
            PrCity("المجمعة", 25.9039, 45.3456, "Asia/Riyadh", "السعودية"),
            PrCity("الزلفي", 26.2994, 44.8154, "Asia/Riyadh", "السعودية"),
            PrCity("وادي الدواسر", 20.4667, 44.7833, "Asia/Riyadh", "السعودية"),
            PrCity("تربة", 21.2142, 41.6331, "Asia/Riyadh", "السعودية"),
            PrCity("رنية", 21.2667, 42.85, "Asia/Riyadh", "السعودية"),
            PrCity("الخرمة", 21.9167, 42.05, "Asia/Riyadh", "السعودية"),
            PrCity("صنعاء", 15.3694, 44.191, "Asia/Aden", "اليمن"),
            PrCity("عدن", 12.7855, 45.0187, "Asia/Aden", "اليمن"),
            PrCity("تعز", 13.5795, 44.0209, "Asia/Aden", "اليمن"),
            PrCity("الحديدة", 14.7978, 42.9545, "Asia/Aden", "اليمن"),
            PrCity("المكلا", 14.5425, 49.1242, "Asia/Aden", "اليمن"),
            PrCity("سيئون", 15.9431, 48.7879, "Asia/Aden", "اليمن"),
            PrCity("تريم", 16.054, 49.0, "Asia/Aden", "اليمن"),
            PrCity("إب", 13.9667, 44.1833, "Asia/Aden", "اليمن"),
            PrCity("ذمار", 14.5427, 44.4051, "Asia/Aden", "اليمن"),
            PrCity("مأرب", 15.4626, 45.3256, "Asia/Aden", "اليمن"),
            PrCity("صعدة", 16.9402, 43.7639, "Asia/Aden", "اليمن"),
            PrCity("حجة", 15.6943, 43.6058, "Asia/Aden", "اليمن"),
            PrCity("عمران", 15.6594, 43.9439, "Asia/Aden", "اليمن"),
            PrCity("البيضاء", 13.985, 45.573, "Asia/Aden", "اليمن"),
            PrCity("عتق", 14.5377, 46.8319, "Asia/Aden", "اليمن"),
            PrCity("الغيضة", 16.2079, 52.176, "Asia/Aden", "اليمن"),
            PrCity("لحج (الحوطة)", 13.0583, 44.8838, "Asia/Aden", "اليمن"),
            PrCity("زنجبار", 13.1287, 45.3803, "Asia/Aden", "اليمن"),
            PrCity("الضالع", 13.6957, 44.7314, "Asia/Aden", "اليمن"),
            PrCity("الكويت", 29.3759, 47.9774, "Asia/Kuwait", "الدول العربية"),
            PrCity("الدوحة", 25.2854, 51.531, "Asia/Qatar", "الدول العربية"),
            PrCity("المنامة", 26.2285, 50.586, "Asia/Bahrain", "الدول العربية"),
            PrCity("أبوظبي", 24.4539, 54.3773, "Asia/Dubai", "الدول العربية"),
            PrCity("دبي", 25.2048, 55.2708, "Asia/Dubai", "الدول العربية"),
            PrCity("الشارقة", 25.3463, 55.4209, "Asia/Dubai", "الدول العربية"),
            PrCity("مسقط", 23.588, 58.3829, "Asia/Muscat", "الدول العربية"),
            PrCity("صلالة", 17.0151, 54.0924, "Asia/Muscat", "الدول العربية"),
            PrCity("بغداد", 33.3152, 44.3661, "Asia/Baghdad", "الدول العربية"),
            PrCity("البصرة", 30.5085, 47.7804, "Asia/Baghdad", "الدول العربية"),
            PrCity("دمشق", 33.5138, 36.2765, "Asia/Damascus", "الدول العربية"),
            PrCity("بيروت", 33.8938, 35.5018, "Asia/Beirut", "الدول العربية"),
            PrCity("عمّان", 31.9454, 35.9284, "Asia/Amman", "الدول العربية"),
            PrCity("القدس", 31.7683, 35.2137, "Asia/Jerusalem", "الدول العربية"),
            PrCity("غزة", 31.5017, 34.4668, "Asia/Gaza", "الدول العربية"),
            PrCity("القاهرة", 30.0444, 31.2357, "Africa/Cairo", "الدول العربية"),
            PrCity("الإسكندرية", 31.2001, 29.9187, "Africa/Cairo", "الدول العربية"),
            PrCity("الخرطوم", 15.5007, 32.5599, "Africa/Khartoum", "الدول العربية"),
            PrCity("طرابلس", 32.8872, 13.1913, "Africa/Tripoli", "الدول العربية"),
            PrCity("تونس", 36.8065, 10.1815, "Africa/Tunis", "الدول العربية"),
            PrCity("الجزائر", 36.7538, 3.0588, "Africa/Algiers", "الدول العربية"),
            PrCity("الرباط", 34.0209, -6.8416, "Africa/Casablanca", "الدول العربية"),
            PrCity("الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca", "الدول العربية"),
            PrCity("نواكشوط", 18.0735, -15.9582, "Africa/Nouakchott", "الدول العربية"),
            PrCity("مقديشو", 2.0469, 45.3182, "Africa/Mogadishu", "الدول العربية"),
            PrCity("جيبوتي", 11.5721, 43.1456, "Africa/Djibouti", "الدول العربية"),
            PrCity("إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", "العالم"),
            PrCity("أنقرة", 39.9334, 32.8597, "Europe/Istanbul", "العالم"),
            PrCity("طهران", 35.6892, 51.389, "Asia/Tehran", "العالم"),
            PrCity("إسلام آباد", 33.6844, 73.0479, "Asia/Karachi", "العالم"),
            PrCity("كراتشي", 24.8607, 67.0011, "Asia/Karachi", "العالم"),
            PrCity("نيودلهي", 28.6139, 77.209, "Asia/Kolkata", "العالم"),
            PrCity("دكا", 23.8103, 90.4125, "Asia/Dhaka", "العالم"),
            PrCity("جاكرتا", -6.2088, 106.8456, "Asia/Jakarta", "العالم"),
            PrCity("كوالالمبور", 3.139, 101.6869, "Asia/Kuala_Lumpur", "العالم"),
            PrCity("سنغافورة", 1.3521, 103.8198, "Asia/Singapore", "العالم"),
            PrCity("بكين", 39.9042, 116.4074, "Asia/Shanghai", "العالم"),
            PrCity("طوكيو", 35.6762, 139.6503, "Asia/Tokyo", "العالم"),
            PrCity("موسكو", 55.7558, 37.6173, "Europe/Moscow", "العالم"),
            PrCity("لندن", 51.5074, -0.1278, "Europe/London", "العالم"),
            PrCity("باريس", 48.8566, 2.3522, "Europe/Paris", "العالم"),
            PrCity("برلين", 52.52, 13.405, "Europe/Berlin", "العالم"),
            PrCity("مدريد", 40.4168, -3.7038, "Europe/Madrid", "العالم"),
            PrCity("روما", 41.9028, 12.4964, "Europe/Rome", "العالم"),
            PrCity("أمستردام", 52.3676, 4.9041, "Europe/Amsterdam", "العالم"),
            PrCity("بروكسل", 50.8503, 4.3517, "Europe/Brussels", "العالم"),
            PrCity("ستوكهولم", 59.3293, 18.0686, "Europe/Stockholm", "العالم"),
            PrCity("نيويورك", 40.7128, -74.006, "America/New_York", "العالم"),
            PrCity("واشنطن", 38.9072, -77.0369, "America/New_York", "العالم"),
            PrCity("تورنتو", 43.6532, -79.3832, "America/Toronto", "العالم"),
            PrCity("لوس أنجلوس", 34.0522, -118.2437, "America/Los_Angeles", "العالم"),
            PrCity("سيدني", -33.8688, 151.2093, "Australia/Sydney", "العالم"),
            PrCity("نيروبي", -1.2921, 36.8219, "Africa/Nairobi", "العالم"),
            PrCity("لاغوس", 6.5244, 3.3792, "Africa/Lagos", "العالم"),
            PrCity("أديس أبابا", 9.03, 38.74, "Africa/Addis_Ababa", "العالم"),
            PrCity("باكو", 40.4093, 49.8671, "Asia/Baku", "العالم"),
            PrCity("طشقند", 41.2995, 69.2401, "Asia/Tashkent", "العالم"),
        )
    }
}

internal enum class PrPrayer(val title: String, val isPrayer: Boolean) {
    FAJR("الفجر", true), SUNRISE("الشروق", false), DHUHR("الظهر", true), ASR("العصر", true), MAGHRIB("المغرب", true), ISHA("العشاء", true)
}

/** Offline prayer-time engine (solar position after Meeus / praytimes.org). */
internal object PrEngine {
    private fun r(d: Double) = Math.toRadians(d)
    private fun g(x: Double) = Math.toDegrees(x)
    private fun fixA(a: Double): Double { val v = a % 360.0; return if (v < 0) v + 360 else v }
    private fun fixH(h: Double): Double { val v = h % 24.0; return if (v < 0) v + 24 else v }

    fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = (y / 100).toDouble()
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private fun sun(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val gg = fixA(357.529 + 0.98560028 * dd)
        val q = fixA(280.459 + 0.98564736 * dd)
        val l = fixA(q + 1.915 * sin(r(gg)) + 0.020 * sin(r(2 * gg)))
        val e = 23.439 - 0.00000036 * dd
        val ra = fixH(g(atan2(cos(r(e)) * sin(r(l)), cos(r(l)))) / 15)
        return g(asin(sin(r(e)) * sin(r(l)))) to (q / 15 - ra)
    }

    /** Local hours for the civil date. */
    fun hours(y: Int, m: Int, d: Int, lat: Double, lng: Double, tz: Double, method: PrMethod, hanafi: Boolean, ramadan: Boolean): Map<PrPrayer, Double> {
        val jd = julian(y, m, d) - lng / (15 * 24)
        fun mid(t: Double) = fixH(12 - sun(jd + t).second)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val dec = sun(jd + t).first
            val noon = mid(t)
            val c = (-sin(r(angle)) - sin(r(dec)) * sin(r(lat))) / (cos(r(dec)) * cos(r(lat)))
            if (c < -1 || c > 1) return Double.NaN
            val tt = g(acos(c)) / 15
            return noon + if (ccw) -tt else tt
        }
        fun asr(f: Double, t: Double): Double {
            val dec = sun(jd + t).first
            return angleTime(-g(atan(1 / (f + tan(r(abs(lat - dec)))))), t, false)
        }
        val t = mutableMapOf(PrPrayer.FAJR to 5.0, PrPrayer.SUNRISE to 6.0, PrPrayer.DHUHR to 12.0, PrPrayer.ASR to 13.0, PrPrayer.MAGHRIB to 18.0, PrPrayer.ISHA to 18.0)
        repeat(2) {
            val p = t.mapValues { (if (it.value.isNaN()) 12.0 else it.value) / 24 }
            t[PrPrayer.FAJR] = angleTime(method.fajr, p[PrPrayer.FAJR]!!, true)
            t[PrPrayer.SUNRISE] = angleTime(0.833, p[PrPrayer.SUNRISE]!!, true)
            t[PrPrayer.DHUHR] = mid(p[PrPrayer.DHUHR]!!)
            t[PrPrayer.ASR] = asr(if (hanafi) 2.0 else 1.0, p[PrPrayer.ASR]!!)
            t[PrPrayer.MAGHRIB] = angleTime(0.833, p[PrPrayer.MAGHRIB]!!, false)
            t[PrPrayer.ISHA] = method.isha?.let { angleTime(it, p[PrPrayer.ISHA]!!, false) } ?: 0.0
        }
        val adj = tz - lng / 15
        val out = t.mapValues { it.value + adj }.toMutableMap()
        if (method.isha == null) {
            val extra = if (method.id == "umm" && ramadan) 120.0 else method.ishaMinutes
            out[PrPrayer.ISHA] = out[PrPrayer.MAGHRIB]!! + extra / 60
        }
        return out
    }

    /** Epoch millis of each time on the civil day containing [atMillis] in [zone]. */
    fun day(atMillis: Long, lat: Double, lng: Double, zone: TimeZone, method: PrMethod, hanafi: Boolean): Map<PrPrayer, Long> {
        val c = Calendar.getInstance(zone).apply { timeInMillis = atMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val start = c.timeInMillis
        val tz = zone.getOffset(start + 12 * 3600_000L) / 3600_000.0
        val h = hours(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lng, tz, method, hanafi, isRamadan(start + 12 * 3600_000L, zone))
        return h.filterValues { !it.isNaN() }.mapValues { start + (it.value * 60).roundToLong() * 60_000L }
    }

    fun isRamadan(millis: Long, zone: TimeZone): Boolean = try {
        val ic = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone(zone.id))
        ic.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        ic.timeInMillis = millis
        ic.get(android.icu.util.Calendar.MONTH) == 8
    } catch (e: Throwable) { false }

    fun hijri(millis: Long, zone: TimeZone): String = try {
        val ic = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone(zone.id))
        ic.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        ic.timeInMillis = millis
        val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        "${ic.get(android.icu.util.Calendar.DAY_OF_MONTH)} ${months[ic.get(android.icu.util.Calendar.MONTH)]} ${ic.get(android.icu.util.Calendar.YEAR)} هـ"
    } catch (e: Throwable) { "" }
}
