package com.alsiddiq.app.domain

import kotlinx.serialization.Serializable

/**
 * v3.8 «حضور وغياب المعلمين»: one mark per teacher per day (admin only). day = "YYYY-MM-DD".
 * v3.9: status = present | absent | excused (حاضر / غائب / مستأذن); checkIn / checkOut = "HH:mm" («حضور و انصراف»), optional.
 */
@Serializable
data class TeacherMark(
    val teacherId: String,
    val day: String,
    val status: String,
    val checkIn: String? = null,
    val checkOut: String? = null
) {
    val present: Boolean get() = status == TeacherStatus.PRESENT
    val excused: Boolean get() = status == TeacherStatus.EXCUSED
    val absent: Boolean get() = status == TeacherStatus.ABSENT
}

/** The three teacher statuses; the server column accepts exactly these words. */
object TeacherStatus {
    const val PRESENT = "present"
    const val ABSENT = "absent"
    const val EXCUSED = "excused"
    val ALL = listOf(PRESENT, ABSENT, EXCUSED)
    fun label(s: String?): String = when (s) { PRESENT -> "حاضر"; ABSENT -> "غائب"; EXCUSED -> "مستأذن"; else -> "" }
}

/**
 * v3.9: Friday is a rest day. No attendance (students or teachers) and no recitation is recorded on a Friday;
 * the records run Saturday to Thursday, each with its date.
 */
object RestDay {
    fun isRest(d: java.time.LocalDate): Boolean = d.dayOfWeek == java.time.DayOfWeek.FRIDAY
    /** The nearest record day on or before d (a Friday goes back to Thursday). */
    fun lastWorkDayOnOrBefore(d: java.time.LocalDate): java.time.LocalDate { var x = d; while (isRest(x)) x = x.minusDays(1); return x }
    /** The previous record day before d (skips Friday). */
    fun prevWorkDay(d: java.time.LocalDate): java.time.LocalDate { var x = d.minusDays(1); while (isRest(x)) x = x.minusDays(1); return x }
    /** The next record day after d, never later than today (skips Friday). Null when there is none yet. */
    fun nextWorkDay(d: java.time.LocalDate, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
        var x = d.plusDays(1); while (isRest(x)) x = x.plusDays(1)
        return if (x.isAfter(today)) null else x
    }
    const val MESSAGE = "الجمعة يوم راحة، ما يُسجَّل فيه شي. السجل من السبت إلى الخميس."
}

/** v3.8 «الميزانية»: income (a student's subscription payment) or expense (note required). Admin only. */
@Serializable
data class BudgetEntry(
    val id: String,
    val kind: String,              // "income" | "expense"
    val amount: Double,
    val day: String,               // "YYYY-MM-DD"
    val studentId: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isIncome: Boolean get() = kind == INCOME
    companion object { const val INCOME = "income"; const val EXPENSE = "expense" }
}

/** v3.8 «كشف الاشتراكات»: a student's month (month = "YYYY-MM-01"). No mark = لم يسلم. */
@Serializable
data class SubscriptionMark(val studentId: String, val month: String, val paid: Boolean, val amount: Double? = null)
