package com.alsiddiq.app.domain

/** His text rules (Nov 8, 1:03 PM). */
object ArabicText {
    /** A student count with the correct Arabic plural: لا طلاب، طالب واحد، طالبان، 3–10 طلاب، 11+ طالب. */
    fun students(n: Int): String = when {
        n <= 0 -> "لا طلاب"
        n == 1 -> "طالب واحد"
        n == 2 -> "طالبان"
        n in 3..10 -> "$n طلاب"
        else -> "$n طالب"
    }

    /** Circle levels: «تمهيدي» is now called «مبتدئ». A stored old value stays as it is and is shown as «مبتدئ». */
    val LEVELS = listOf("مبتدئ", "متوسط", "متقدم", "مراجعة", "إجازة")
    fun level(stored: String): String = if (stored.trim() == "تمهيدي") "مبتدئ" else stored

    /** v3.5: «الرسوم: ٥٬٠٠٠ ريال يمني شهرياً» (null when no fee is set). */
    val FEE_PERIODS = listOf("monthly" to "شهرياً", "term" to "للفصل", "once" to "مرة واحدة")
    fun feePeriod(p: String?): String = FEE_PERIODS.firstOrNull { it.first == (p ?: "monthly") }?.second ?: "شهرياً"
    fun fee(yer: Int?, period: String?): String? = when {
        yer == null -> null
        yer == 0 -> "الرسوم: مجاناً"
        else -> "الرسوم: ${arabicThousands(yer)} ريال يمني ${feePeriod(period)}"
    }
    fun arabicThousands(n: Int): String = "%,d".format(java.util.Locale.US, n)
        .map { c -> when (c) { in '0'..'9' -> '٠' + (c - '0'); ',' -> '٬'; else -> c } }.joinToString("")
}
