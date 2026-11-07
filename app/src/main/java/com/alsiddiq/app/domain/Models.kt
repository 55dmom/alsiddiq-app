package com.alsiddiq.app.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role(val label: String) {
    ADMIN("الإدارة"),
    TEACHER("معلم"),
    /** The general password: read-only view of the whole centre. */
    VIEWER("عرض عام")
}

@Serializable
data class Member(
    val id: String,
    val fullName: String,
    val phone: String,
    val role: Role,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Circle(
    val id: String,
    val name: String,
    val teacherId: String?,
    val level: String = "",
    val schedule: String = "",
    val capacity: Int = 20,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class StudentStatus(val label: String) { ACTIVE("منتظم"), PAUSED("متوقف مؤقتاً"), GRADUATED("خاتم") }

@Serializable
data class Student(
    val id: String,
    val fullName: String,
    val phone: String = "",
    val birthYear: Int? = null,
    val circleId: String?,
    val joinedOn: String,
    val goalJuz: Int = 30,
    val status: StudentStatus = StudentStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class RecordKind(val label: String, val short: String) {
    HIFZ("حفظ جديد", "حفظ"),
    TASMEE("تسميع", "تسميع"),
    MURAJA("مراجعة", "مراجعة")
}

@Serializable
enum class Grade(val label: String, val score: Int) {
    EXCELLENT("ممتاز", 5),
    VERY_GOOD("جيد جداً", 4),
    GOOD("جيد", 3),
    PASS("مقبول", 2),
    WEAK("يحتاج إعادة", 1)
}

@Serializable
data class Recitation(
    val id: String,
    val studentId: String,
    val circleId: String?,
    val teacherId: String,
    val kind: RecordKind,
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val grade: Grade,
    val mistakes: Int = 0,
    val notes: String = "",
    val recordedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class AttendanceStatus(val label: String) {
    PRESENT("حاضر"), LATE("متأخر"), EXCUSED("مستأذن"), ABSENT("غائب")
}

@Serializable
data class Attendance(
    val id: String,
    val studentId: String,
    val circleId: String?,
    /** ISO date yyyy-MM-dd */
    val day: String,
    val status: AttendanceStatus,
    val recordedBy: String,
    val recordedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Reward(
    val id: String,
    val studentId: String,
    val points: Int,
    val reason: String,
    val awardedBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class Audience(val label: String) {
    ALL("الجميع"), TEACHERS("المعلمون"), PARENTS("أولياء الأمور"), STUDENTS("الطلاب")
}

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val audience: Audience,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppNotification(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val kind: String = "info",
    val refId: String? = null,
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CenterInfo(
    val name: String = "مركز الصديق لتعليم القرآن الكريم",
    val city: String = ""
)

/** Everything the signed-in user is allowed to see, as one immutable snapshot. */
@Serializable
data class CenterSnapshot(
    val center: CenterInfo = CenterInfo(),
    val members: List<Member> = emptyList(),
    val circles: List<Circle> = emptyList(),
    val students: List<Student> = emptyList(),
    val recitations: List<Recitation> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val notifications: List<AppNotification> = emptyList()
) {
    fun member(id: String?): Member? = members.firstOrNull { it.id == id }
    fun circle(id: String?): Circle? = circles.firstOrNull { it.id == id }
    fun student(id: String?): Student? = students.firstOrNull { it.id == id }
    fun studentsIn(circleId: String): List<Student> = students.filter { it.circleId == circleId }
    fun teachers(): List<Member> = members.filter { it.role == Role.TEACHER && it.active }
    fun circlesOf(teacherId: String): List<Circle> = circles.filter { it.teacherId == teacherId }
}
