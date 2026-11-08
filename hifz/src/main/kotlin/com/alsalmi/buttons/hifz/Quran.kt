package com.alsalmi.buttons.hifz

import android.content.Context

/** The Qur'an text (Tanzil Uthmani 1.1, bundled verbatim in assets/quran-uthmani.txt) and its metadata (Tanzil Quran Metadata 1.0). */
internal object HfQuran {
    /** name, ayah count, Makki? */
    val suras: List<Triple<String, Int, Boolean>> = listOf(
        Triple("الفاتحة", 7, true),
        Triple("البقرة", 286, false),
        Triple("آل عمران", 200, false),
        Triple("النساء", 176, false),
        Triple("المائدة", 120, false),
        Triple("الأنعام", 165, true),
        Triple("الأعراف", 206, true),
        Triple("الأنفال", 75, false),
        Triple("التوبة", 129, false),
        Triple("يونس", 109, true),
        Triple("هود", 123, true),
        Triple("يوسف", 111, true),
        Triple("الرعد", 43, false),
        Triple("إبراهيم", 52, true),
        Triple("الحجر", 99, true),
        Triple("النحل", 128, true),
        Triple("الإسراء", 111, true),
        Triple("الكهف", 110, true),
        Triple("مريم", 98, true),
        Triple("طه", 135, true),
        Triple("الأنبياء", 112, true),
        Triple("الحج", 78, false),
        Triple("المؤمنون", 118, true),
        Triple("النور", 64, false),
        Triple("الفرقان", 77, true),
        Triple("الشعراء", 227, true),
        Triple("النمل", 93, true),
        Triple("القصص", 88, true),
        Triple("العنكبوت", 69, true),
        Triple("الروم", 60, true),
        Triple("لقمان", 34, true),
        Triple("السجدة", 30, true),
        Triple("الأحزاب", 73, false),
        Triple("سبأ", 54, true),
        Triple("فاطر", 45, true),
        Triple("يس", 83, true),
        Triple("الصافات", 182, true),
        Triple("ص", 88, true),
        Triple("الزمر", 75, true),
        Triple("غافر", 85, true),
        Triple("فصلت", 54, true),
        Triple("الشورى", 53, true),
        Triple("الزخرف", 89, true),
        Triple("الدخان", 59, true),
        Triple("الجاثية", 37, true),
        Triple("الأحقاف", 35, true),
        Triple("محمد", 38, false),
        Triple("الفتح", 29, false),
        Triple("الحجرات", 18, false),
        Triple("ق", 45, true),
        Triple("الذاريات", 60, true),
        Triple("الطور", 49, true),
        Triple("النجم", 62, true),
        Triple("القمر", 55, true),
        Triple("الرحمن", 78, false),
        Triple("الواقعة", 96, true),
        Triple("الحديد", 29, false),
        Triple("المجادلة", 22, false),
        Triple("الحشر", 24, false),
        Triple("الممتحنة", 13, false),
        Triple("الصف", 14, false),
        Triple("الجمعة", 11, false),
        Triple("المنافقون", 11, false),
        Triple("التغابن", 18, false),
        Triple("الطلاق", 12, false),
        Triple("التحريم", 12, false),
        Triple("الملك", 30, true),
        Triple("القلم", 52, true),
        Triple("الحاقة", 52, true),
        Triple("المعارج", 44, true),
        Triple("نوح", 28, true),
        Triple("الجن", 28, true),
        Triple("المزمل", 20, true),
        Triple("المدثر", 56, true),
        Triple("القيامة", 40, true),
        Triple("الإنسان", 31, false),
        Triple("المرسلات", 50, true),
        Triple("النبأ", 40, true),
        Triple("النازعات", 46, true),
        Triple("عبس", 42, true),
        Triple("التكوير", 29, true),
        Triple("الانفطار", 19, true),
        Triple("المطففين", 36, true),
        Triple("الانشقاق", 25, true),
        Triple("البروج", 22, true),
        Triple("الطارق", 17, true),
        Triple("الأعلى", 19, true),
        Triple("الغاشية", 26, true),
        Triple("الفجر", 30, true),
        Triple("البلد", 20, true),
        Triple("الشمس", 15, true),
        Triple("الليل", 21, true),
        Triple("الضحى", 11, true),
        Triple("الشرح", 8, true),
        Triple("التين", 8, true),
        Triple("العلق", 19, true),
        Triple("القدر", 5, true),
        Triple("البينة", 8, false),
        Triple("الزلزلة", 8, false),
        Triple("العاديات", 11, true),
        Triple("القارعة", 11, true),
        Triple("التكاثر", 8, true),
        Triple("العصر", 3, true),
        Triple("الهمزة", 9, true),
        Triple("الفيل", 5, true),
        Triple("قريش", 4, true),
        Triple("الماعون", 7, true),
        Triple("الكوثر", 3, true),
        Triple("الكافرون", 6, true),
        Triple("النصر", 3, false),
        Triple("المسد", 5, true),
        Triple("الإخلاص", 4, true),
        Triple("الفلق", 5, true),
        Triple("الناس", 6, true)
    )
    /** first (surah, ayah) of each juz 1…30 */
    val juz: List<Pair<Int, Int>> = listOf(1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41, 9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56, 29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1)
    /** first ayah key (surah*1000+ayah) of each Madinah-mushaf page 1…604 */
    val pages: IntArray = intArrayOf(1001, 2001, 2006, 2017, 2025, 2030, 2038, 2049, 2058, 2062, 2070, 2077, 2084, 2089, 2094, 2102, 2106, 2113, 2120, 2127, 2135, 2142, 2146, 2154, 2164, 2170, 2177, 2182, 2187, 2191, 2197, 2203, 2211, 2216, 2220, 2225, 2231, 2234, 2238, 2246, 2249, 2253, 2257, 2260, 2265, 2270, 2275, 2282, 2283, 3001, 3010, 3016, 3023, 3030, 3038, 3046, 3053, 3062, 3071, 3078, 3084, 3092, 3101, 3109, 3116, 3122, 3133, 3141, 3149, 3154, 3158, 3166, 3174, 3181, 3187, 3195, 4001, 4007, 4012, 4015, 4020, 4024, 4027, 4034, 4038, 4045, 4052, 4060, 4066, 4075, 4080, 4087, 4092, 4095, 4102, 4106, 4114, 4122, 4128, 4135, 4141, 4148, 4155, 4163, 4171, 4176, 5003, 5006, 5010, 5014, 5018, 5024, 5032, 5037, 5042, 5046, 5051, 5058, 5065, 5071, 5077, 5083, 5090, 5096, 5104, 5109, 5114, 6001, 6009, 6019, 6028, 6036, 6045, 6053, 6060, 6069, 6074, 6082, 6091, 6095, 6102, 6111, 6119, 6125, 6132, 6138, 6143, 6147, 6152, 6158, 7001, 7012, 7023, 7031, 7038, 7044, 7052, 7058, 7068, 7074, 7082, 7088, 7096, 7105, 7121, 7131, 7138, 7144, 7150, 7156, 7160, 7164, 7171, 7179, 7188, 7196, 8001, 8009, 8017, 8026, 8034, 8041, 8046, 8053, 8062, 8070, 9001, 9007, 9014, 9021, 9027, 9032, 9037, 9041, 9048, 9055, 9062, 9069, 9073, 9080, 9087, 9094, 9100, 9107, 9112, 9118, 9123, 10001, 10007, 10015, 10021, 10026, 10034, 10043, 10054, 10062, 10071, 10079, 10089, 10098, 10107, 11006, 11013, 11020, 11029, 11038, 11046, 11054, 11063, 11072, 11082, 11089, 11098, 11109, 11118, 12005, 12015, 12023, 12031, 12038, 12044, 12053, 12064, 12070, 12079, 12087, 12096, 12104, 13001, 13006, 13014, 13019, 13029, 13035, 13043, 14006, 14011, 14019, 14025, 14034, 14043, 15001, 15016, 15032, 15052, 15071, 15091, 16007, 16015, 16027, 16035, 16043, 16055, 16065, 16073, 16080, 16088, 16094, 16103, 16111, 16119, 17001, 17008, 17018, 17028, 17039, 17050, 17059, 17067, 17076, 17087, 17097, 17105, 18005, 18016, 18021, 18028, 18035, 18046, 18054, 18062, 18075, 18084, 18098, 19001, 19012, 19026, 19039, 19052, 19065, 19077, 19096, 20013, 20038, 20052, 20065, 20077, 20088, 20099, 20114, 20126, 21001, 21011, 21025, 21036, 21045, 21058, 21073, 21082, 21091, 21102, 22001, 22006, 22016, 22024, 22031, 22039, 22047, 22056, 22065, 22073, 23001, 23018, 23028, 23043, 23060, 23075, 23090, 23105, 24001, 24011, 24021, 24028, 24032, 24037, 24044, 24054, 24059, 24062, 25003, 25012, 25021, 25033, 25044, 25056, 25068, 26001, 26020, 26040, 26061, 26084, 26112, 26137, 26160, 26184, 26207, 27001, 27014, 27023, 27036, 27045, 27056, 27064, 27077, 27089, 28006, 28014, 28022, 28029, 28036, 28044, 28051, 28060, 28071, 28078, 28085, 29007, 29015, 29024, 29031, 29039, 29046, 29053, 29064, 30006, 30016, 30025, 30033, 30042, 30051, 31001, 31012, 31020, 31029, 32001, 32012, 32021, 33001, 33007, 33016, 33023, 33031, 33036, 33044, 33051, 33055, 33063, 34001, 34008, 34015, 34023, 34032, 34040, 34049, 35004, 35012, 35019, 35031, 35039, 35045, 36013, 36028, 36041, 36055, 36071, 37001, 37025, 37052, 37077, 37103, 37127, 37154, 38001, 38017, 38027, 38043, 38062, 38084, 39006, 39011, 39022, 39032, 39041, 39048, 39057, 39068, 39075, 40008, 40017, 40026, 40034, 40041, 40050, 40059, 40067, 40078, 41001, 41012, 41021, 41030, 41039, 41047, 42001, 42011, 42016, 42023, 42032, 42045, 42052, 43011, 43023, 43034, 43048, 43061, 43074, 44001, 44019, 44040, 45001, 45014, 45023, 45033, 46006, 46015, 46021, 46029, 47001, 47012, 47020, 47030, 48001, 48010, 48016, 48024, 48029, 49005, 49012, 50001, 50016, 50036, 51007, 51031, 51052, 52015, 52032, 53001, 53027, 53045, 54007, 54028, 54050, 55017, 55041, 55068, 56017, 56051, 56077, 57004, 57012, 57019, 57025, 58001, 58007, 58012, 58022, 59004, 59010, 59017, 60001, 60006, 60012, 61006, 62001, 62009, 63005, 64001, 64010, 65001, 65006, 66001, 66008, 67001, 67013, 67027, 68016, 68043, 69009, 69035, 70011, 70040, 71011, 72001, 72014, 73001, 73020, 74018, 74048, 75020, 76006, 76026, 77020, 78001, 78031, 79016, 80001, 81001, 82001, 83007, 83035, 85001, 86001, 87016, 89001, 89024, 91001, 92015, 95001, 97001, 98008, 100010, 103001, 106001, 109001, 112001)
    const val PAGE_COUNT = 604
    const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"

    @Volatile private var ayat: Array<Array<String>>? = null

    fun load(ctx: Context): Array<Array<String>> {
        ayat?.let { return it }
        synchronized(this) {
            ayat?.let { return it }
            val out = Array(114) { Array(suras[it].second) { "" } }
            ctx.applicationContext.assets.open("quran-uthmani.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.forEach { line ->
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val p = line.split('|', limit = 3)
                    val s = p.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val a = p.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    if (s in 1..114 && a in 1..out[s - 1].size && p.size == 3) out[s - 1][a - 1] = p[2]
                }
            }
            ayat = out
            return out
        }
    }


    /** Tanzil puts the basmala at the start of ayah 1 (all surahs but 1 and 9). Returns (basmala, rest), both verbatim. */
    fun split(ayat: Array<Array<String>>, s: Int, a: Int): Pair<String?, String> {
        val t = ayat[s - 1][a - 1]
        if (a != 1 || s == 1 || s == 9) return null to t
        val w = t.split(' ')
        if (w.size <= 4) return null to t
        return w.take(4).joinToString(" ") to w.drop(4).joinToString(" ")
    }

    fun key(s: Int, a: Int) = s * 1000 + a

    fun page(s: Int, a: Int): Int {
        val k = key(s, a)
        var lo = 0; var hi = pages.size - 1
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (pages[mid] <= k) lo = mid else hi = mid - 1 }
        return lo + 1
    }
    fun juzOf(s: Int, a: Int): Int {
        val k = key(s, a); var j = 1
        juz.forEachIndexed { i, x -> if (key(x.first, x.second) <= k) j = i + 1 }
        return j
    }
    fun next(s: Int, a: Int): Pair<Int, Int>? = if (a < suras[s - 1].second) s to a + 1 else if (s < 114) s + 1 to 1 else null

    fun ayahsOnPage(p: Int): List<Pair<Int, Int>> {
        if (p !in 1..PAGE_COUNT) return emptyList()
        var cur: Pair<Int, Int>? = pages[p - 1] / 1000 to pages[p - 1] % 1000
        val end = if (p < PAGE_COUNT) pages[p] else Int.MAX_VALUE
        val out = mutableListOf<Pair<Int, Int>>()
        while (cur != null && key(cur.first, cur.second) < end) { out.add(cur); cur = next(cur.first, cur.second) }
        return out
    }

    /** Arabic-Indic ayah marker as printed in the Mushaf, e.g. ﴿١٢﴾ */
    fun marker(n: Int): String = "﴿" + n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("") + "﴾"
}
