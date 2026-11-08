package com.alsiddiq.app.ui.theme

import android.content.Context
import android.icu.util.IslamicCalendar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar

/**
 * «ثيمات المناسبات»: forced while the occasion lasts (over the user's own choice); afterwards his choice comes back.
 * Texts, palette words and icons come from the bundled files as he wrote them:
 *   assets/occasions/tafaul_occasion_themes.json (his file, unchanged) and assets/occasions/occasion_themes_national.json.
 * The files carry no dates, so the dates live in [Occasions.dates] below.
 */
data class DayRange(val fromMonth: Int, val fromDay: Int, val toMonth: Int, val toDay: Int) {
    /** month 0 = every month (e.g. الأيام البيض 13–15 of each Hijri month). */
    fun contains(month: Int, day: Int): Boolean {
        if (fromMonth == 0) return day in fromDay..toDay
        val x = month * 100 + day; val a = fromMonth * 100 + fromDay; val b = toMonth * 100 + toDay
        return if (a <= b) x in a..b else (x >= a || x <= b)
    }
}

data class OccasionCard(val title: String, val body: String, val icon: String, val color: String)

data class Occasion(
    val id: String,
    val name: String,
    val palette: AppPalette,
    val card: OccasionCard?,
    /** Hijri months 1..12 (محرم = 1 … رمضان = 9، شوال = 10، ذو الحجة = 12), Umm al-Qura. */
    val hijri: DayRange? = null,
    /** Gregorian months 1..12. */
    val gregorian: DayRange? = null,
    /** Higher wins when two are active the same day. */
    val priority: Int = 0,
)

object Occasions {
    /** Dates and priority per occasion id (the files have none). National days (Gregorian) win over everything on their day. */
    private class Rule(val hijri: DayRange? = null, val gregorian: DayRange? = null, val priority: Int)
    private val dates: Map<String, Rule> = mapOf(
        "ramadan" to Rule(hijri = DayRange(9, 1, 9, 30), priority = 40),
        "eid-fitr" to Rule(hijri = DayRange(10, 1, 10, 3), priority = 50),
        "dhul-hijjah" to Rule(hijri = DayRange(12, 1, 12, 8), priority = 20),
        "arafah" to Rule(hijri = DayRange(12, 9, 12, 9), priority = 50),
        "eid-adha" to Rule(hijri = DayRange(12, 10, 12, 13), priority = 50),
        "hijri-new-year" to Rule(hijri = DayRange(1, 1, 1, 1), priority = 40),
        "ashura" to Rule(hijri = DayRange(1, 9, 1, 10), priority = 40),
        "white-days" to Rule(hijri = DayRange(0, 13, 0, 15), priority = 10),
        "sep26" to Rule(gregorian = DayRange(9, 26, 9, 26), priority = 100),
        "may22" to Rule(gregorian = DayRange(5, 22, 5, 22), priority = 100),
    )
    private val files = listOf("occasions/tafaul_occasion_themes.json", "occasions/occasion_themes_national.json")

    var all: List<Occasion> = emptyList()
        private set

    /** Debug/CI only: simulate a date. "yyyy-MM-dd" (Gregorian, converted with the phone's Hijri calendar) or "h:M-D" (a Hijri month-day). */
    var simulate: String? = null

    fun init(ctx: Context) {
        if (all.isNotEmpty()) return
        val list = mutableListOf<Occasion>()
        for (f in files) runCatching {
            val root = Json.parseToJsonElement(ctx.assets.open(f).bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
            for ((key, v) in root) runCatching { parse(key, v.jsonObject)?.let { list += it } }
        }
        all = list
    }

    private fun JsonObject.str(vararg path: String): String? {
        var o: JsonObject = this
        for (k in path.dropLast(1)) o = (o[k] as? JsonObject) ?: return null
        return runCatching { o[path.last()]?.jsonPrimitive?.content }.getOrNull()
    }

    private fun parse(key: String, o: JsonObject): Occasion? {
        val rule = dates[key] ?: return null
        val force = o.str("theme", "force")?.toBooleanStrictOrNull() ?: true
        if (!force) return null
        val cardObj = runCatching { (o["home"] as JsonObject)["cards"]!!.let { it as kotlinx.serialization.json.JsonArray }.first().jsonObject }.getOrNull()
        val card = cardObj?.let { c ->
            OccasionCard(c.str("title", "ar") ?: "", c.str("body", "ar") ?: "", c.str("icon") ?: "", c.str("color") ?: "gold")
        }
        val palette = OccasionPalettes.make(key, card?.title ?: key, o.str("theme", "palette") ?: "", o.str("theme", "accent") ?: "gold")
        return Occasion(key, card?.title ?: key, palette, card, rule.hijri, rule.gregorian, rule.priority)
    }

    fun activeNow(now: Long = System.currentTimeMillis()): Occasion? {
        if (all.isEmpty()) return null
        var gm: Int; var gd: Int; var hm = 0; var hd = 0
        val sim = simulate
        if (sim != null && sim.startsWith("h:")) {
            val p = sim.removePrefix("h:").split("-"); hm = p[0].toInt(); hd = p[1].toInt(); gm = 0; gd = 0
        } else {
            val g = Calendar.getInstance()
            if (sim != null) { val p = sim.split("-"); g.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0) } else g.timeInMillis = now
            gm = g.get(Calendar.MONTH) + 1; gd = g.get(Calendar.DAY_OF_MONTH)
            runCatching {
                val h = IslamicCalendar().apply { calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA; timeInMillis = g.timeInMillis }
                hm = h.get(android.icu.util.Calendar.MONTH) + 1; hd = h.get(android.icu.util.Calendar.DAY_OF_MONTH)
            }
        }
        return all.filter { o -> (gm > 0 && o.gregorian?.contains(gm, gd) == true) || (hm > 0 && o.hijri?.contains(hm, hd) == true) }
            .maxByOrNull { it.priority }
    }
}

/** His palette and accent words → colour sets (the words are his; the exact colours are ours). */
object OccasionPalettes {
    private fun accentOf(word: String, dark: Boolean): Pair<Color, Color> = when (word) {
        "orange" -> if (dark) Color(0xFFE59A4A) to Color(0xFF3A2A1A) else Color(0xFFD9822B) to Color(0xFFFCEBD9)
        "sky" -> if (dark) Color(0xFF5BB8E8) to Color(0xFF16303D) else Color(0xFF2B95CC) to Color(0xFFE1F2FA)
        "indigo" -> if (dark) Color(0xFF818CF8) to Color(0xFF23264A) else Color(0xFF4F46E5) to Color(0xFFE8E7FC)
        "sand" -> if (dark) Color(0xFFD6BC93) to Color(0xFF37302A) else Color(0xFFB8935F) to Color(0xFFF6EEE2)
        "teal" -> if (dark) Color(0xFF2DD4BF) to Color(0xFF15332F) else Color(0xFF0F8F84) to Color(0xFFDDF3F1)
        "emerald" -> if (dark) Color(0xFF34D399) to Color(0xFF173A2C) else Color(0xFF0E8A5F) to Color(0xFFDDF3EA)
        "red" -> if (dark) Color(0xFFF87171) to Color(0xFF3D1C1C) else Color(0xFFCE1126) to Color(0xFFFBE7E9)
        else -> if (dark) Color(0xFFD9B45E) to Color(0xFF392F1B) else Color(0xFFC9A24A) to Color(0xFFFBF3E0) // gold
    }
    /** Card colour words (gold, emerald, orange, teal, sky, indigo, sand, red…). */
    fun cardColor(word: String, dark: Boolean): Color = accentOf(word, dark).first

    fun make(id: String, name: String, word: String, accent: String): AppPalette {
        val base: AppPalette = when (word) {
            "midnight" -> AppPalette(id, name, true,
                navy = Color(0xFF4A58B8), navyMid = Color(0xFF2E3A86), navyDark = Color(0xFF0D1333), navySoft = Color(0xFF1E2650),
                maroon = Color(0xFF9F8CD6), maroonSoft = Color(0xFF2A2547), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF4CB98A), greenSoft = Color(0xFF1B3A2D), orange = Color(0xFFE59A4A), red = Color(0xFFE5675A),
                bg = Color(0xFF0A0F24), card = Color(0xFF141A35), ink = Color(0xFFE8EAF6), muted = Color(0xFF9EA3C0), line = Color(0xFF262D50))
            "emerald" -> AppThemes.Emerald.copy(id = id, name = name)
            "desert" -> AppPalette(id, name, false,
                navy = Color(0xFF8A5A2B), navyMid = Color(0xFFA86E35), navyDark = Color(0xFF5E3C1B), navySoft = Color(0xFFF5E9D8),
                maroon = Color(0xFF9C4A1A), maroonSoft = Color(0xFFF7E6DA), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF4F7A45), greenSoft = Color(0xFFE9F1E3), orange = Color(0xFFCC7A2E), red = Color(0xFFB8432F),
                bg = Color(0xFFFBF5EA), card = Color(0xFFFFFDF8), ink = Color(0xFF2F2416), muted = Color(0xFF7E6F5C), line = Color(0xFFECDFC9))
            "steel" -> AppPalette(id, name, false,
                navy = Color(0xFF37506B), navyMid = Color(0xFF4A6787), navyDark = Color(0xFF22354A), navySoft = Color(0xFFE6ECF2),
                maroon = Color(0xFF2F7F86), maroonSoft = Color(0xFFE1F0F1), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
                bg = Color(0xFFF4F6F8), card = Color(0xFFFFFFFF), ink = Color(0xFF1C2733), muted = Color(0xFF66727F), line = Color(0xFFDEE4EA))
            "ocean" -> AppPalette(id, name, false,
                navy = Color(0xFF0B5C7D), navyMid = Color(0xFF0E7490), navyDark = Color(0xFF073E55), navySoft = Color(0xFFE0F0F6),
                maroon = Color(0xFF155E75), maroonSoft = Color(0xFFDFF0F5), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
                bg = Color(0xFFF2F8FA), card = Color(0xFFFFFFFF), ink = Color(0xFF10242E), muted = Color(0xFF5F7480), line = Color(0xFFD8E7EC))
            "violet" -> AppPalette(id, name, false,
                navy = Color(0xFF4C2A85), navyMid = Color(0xFF5E36A3), navyDark = Color(0xFF33195E), navySoft = Color(0xFFEEE7F8),
                maroon = Color(0xFF7A2E6E), maroonSoft = Color(0xFFF5E6F2), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFC0392B),
                bg = Color(0xFFF7F5FB), card = Color(0xFFFFFFFF), ink = Color(0xFF211A33), muted = Color(0xFF6E6880), line = Color(0xFFE6E1F0))
            "graphite" -> AppPalette(id, name, true,
                navy = Color(0xFF5A6273), navyMid = Color(0xFF3F4552), navyDark = Color(0xFF1B1E23), navySoft = Color(0xFF2A2F38),
                maroon = Color(0xFFA9B3C4), maroonSoft = Color(0xFF2B3038), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF4CB98A), greenSoft = Color(0xFF1B3A2D), orange = Color(0xFFE59A4A), red = Color(0xFFE5675A),
                bg = Color(0xFF111316), card = Color(0xFF1C1F24), ink = Color(0xFFECEDEF), muted = Color(0xFFA3A8B0), line = Color(0xFF2E333A))
            "yemen-flag" -> AppPalette(id, name, false,
                navy = Color(0xFFCE1126), navyMid = Color(0xFF9E0D1D), navyDark = Color(0xFF111111), navySoft = Color(0xFFFBE7E9),
                maroon = Color(0xFF1A1A1A), maroonSoft = Color(0xFFEDEDED), gold = Color.Unspecified, goldSoft = Color.Unspecified,
                green = Color(0xFF2E7D5B), greenSoft = Color(0xFFE5F3EC), orange = Color(0xFFD9822B), red = Color(0xFFCE1126),
                bg = Color(0xFFFAFAFA), card = Color(0xFFFFFFFF), ink = Color(0xFF151515), muted = Color(0xFF6B6B6B), line = Color(0xFFE5E5E5))
            else -> AppThemes.Default.copy(id = id, name = name)
        }
        val (g, gs) = accentOf(accent, base.dark)
        val p = base.copy(gold = g, goldSoft = gs)
        return p.copy(tools = toolsOf(p))
    }

    /** What his tools get for an occasion: derived from the occasion's own colours. */
    fun toolsOf(p: AppPalette) = ToolColors(
        dark = p.dark,
        accent = if (p.dark) lerp(p.navy, Color.White, 0.3f) else p.navy,
        accent2 = p.gold, bg = p.bg, card = p.card, line = p.line, soft = p.navySoft,
        text = p.ink, text2 = p.muted, grad1 = p.navy, grad2 = p.navyDark,
    )
}
