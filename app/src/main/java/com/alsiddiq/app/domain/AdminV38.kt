package com.alsiddiq.app.domain

import kotlinx.serialization.Serializable

/** v3.8 «حضور وغياب المعلمين»: one mark per teacher per day (admin only). day = "YYYY-MM-DD". */
@Serializable
data class TeacherMark(val teacherId: String, val day: String, val present: Boolean)

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
