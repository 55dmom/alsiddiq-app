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
    val createdAt: Long = System.currentTimeMillis(),
    /** v3.1: the shared teachers' account — opens every circle. */
    val allCircles: Boolean = false
)

/** v3.1: a teacher's NAME (no password). Picked when recording, shown in reports and on circles. */
@Serializable
data class TeacherName(val id: String, val name: String, val active: Boolean = true)

@Serializable
data class Circle(
    val id: String,
    val name: String,
    val teacherId: String?,
    val level: String = "",
    val schedule: String = "",
    val capacity: Int = 20,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** v3.1: the teacher's name (from teacher_names). */
    val teacherName: String? = null,
    /** v3.5: the fee in Yemeni rial (null = not set) and its period: monthly | term | once. Set by the admin only. */
    val feeYer: Int? = null,
    val feePeriod: String? = null,
    /** v3.8: the circle's own photo, its «لوحة الحلقة» (storage path; null = the current look). */
    val photoPath: String? = null,
    /** v3.8: more than one teacher per circle (circle_teachers). [teacherId] stays the first one, for older builds. */
    val teacherIds: List<String> = emptyList()
) {
    /** Every teacher of this circle: the old single link plus the v3.8 list. */
    fun allTeacherIds(): List<String> = (listOfNotNull(teacherId) + teacherIds).distinct()
    fun taughtBy(memberId: String?): Boolean = memberId != null && (teacherId == memberId || memberId in teacherIds)
}

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
    val createdAt: Long = System.currentTimeMillis(),
    /** v3.1: path of the photo in the private bucket 'student-photos' (null = no photo). */
    val photoPath: String? = null,
    /** v3.6: «فصل» is a soft delete: the row stays, active = false, with when and by whom. */
    val active: Boolean = true,
    val dismissedAt: Long? = null,
    val dismissedBy: String? = null
)

/** «المتفوقون»: a congratulation card the admin published (everyone sees it once, full screen, on the next app open). */
@Serializable
data class HonorStudent(val id: String, val fullName: String, val photoPath: String? = null, val circleName: String = "", val scoreText: String = "")

@Serializable
data class HonorCard(val id: String, val students: List<HonorStudent>, val message: String = "", val publishedBy: String? = null, val createdAt: Long = 0)

/** v3.6: what a teacher did, for the admin («الأستاذ عبدالله سجّل طالب: محمد أحمد — حلقة النور»). Written by the server only. */
@Serializable
data class AdminEvent(
    val id: String,
    /** student_added | student_dismissed */
    val kind: String,
    val actorId: String? = null,
    val actorName: String = "",
    val studentId: String? = null,
    val studentName: String = "",
    val circleName: String = "",
    val circleId: String? = null,
    val phone: String = "",
    val createdAt: Long = 0,
    val readAt: Long? = null
) {
    val unread get() = readAt == null
}

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
    val recordedAt: Long = System.currentTimeMillis(),
    val byName: String? = null
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
    val recordedAt: Long = System.currentTimeMillis(),
    val byName: String? = null
)

@Serializable
data class Reward(
    val id: String,
    val studentId: String,
    val points: Int,
    val reason: String,
    val awardedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val byName: String? = null
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
    val createdAt: Long = System.currentTimeMillis(),
    val byName: String? = null,
    /** v3.8: optional photo by the admin (bucket student-photos, <center>/announcements/<id>/…). */
    val imagePath: String? = null
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
    val notifications: List<AppNotification> = emptyList(),
    val teacherNames: List<TeacherName> = emptyList(),
    /** v3.6: dismissed students (kept apart, so every list, count and report shows only the active ones). */
    val dismissed: List<Student> = emptyList(),
    /** v3.6: only the admin reads these. */
    val events: List<AdminEvent> = emptyList(),
    /** «المتفوقون»: the published congratulation cards, newest first. */
    val honorCards: List<HonorCard> = emptyList()
) {
    fun member(id: String?): Member? = members.firstOrNull { it.id == id }
    fun circle(id: String?): Circle? = circles.firstOrNull { it.id == id }
    fun student(id: String?): Student? = students.firstOrNull { it.id == id } ?: dismissed.firstOrNull { it.id == id }
    fun studentsIn(circleId: String): List<Student> = students.filter { it.circleId == circleId }
    /** v3.1: teachers are names only (the admin's list). Old per-teacher accounts still count if there are no names. */
    fun teachers(): List<Member> = members.filter { it.role == Role.TEACHER && it.active && !it.allCircles }
    fun activeTeacherNames(): List<TeacherName> = teacherNames.filter { it.active }.sortedBy { it.name }
    fun teacherCount(): Int = activeTeacherNames().size.takeIf { it > 0 } ?: teachers().size
    /** The teacher shown on a circle: its name, else the old account's name. */
    fun teacherOf(c: Circle?): String? =
        c?.allTeacherIds()?.takeIf { it.size > 1 }?.mapNotNull { id -> member(id)?.takeIf { !it.allCircles && it.active }?.fullName }?.takeIf { it.isNotEmpty() }?.joinToString("، ")
            ?: c?.teacherName?.takeIf { it.isNotBlank() } ?: member(c?.teacherId)?.takeIf { !it.allCircles }?.fullName
    /** Who recorded something: the picked name, else the account's name. */
    fun recorder(byName: String?, accountId: String?): String? = byName?.takeIf { it.isNotBlank() } ?: member(accountId)?.fullName
    fun circlesOf(teacherId: String): List<Circle> = circles.filter { it.taughtBy(teacherId) }
}
