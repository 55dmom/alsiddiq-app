package com.alsiddiq.app.domain

/**
 * Client-side mirror of the server's Row Level Security rules. The server is the
 * authority; this only decides what the UI offers and what the demo store accepts.
 */
object Permissions {
    fun canManageCenter(user: Member) = user.role == Role.ADMIN
    fun canManageMembers(user: Member) = user.role == Role.ADMIN
    fun canManageCircles(user: Member) = user.role == Role.ADMIN
    fun canManageStudents(user: Member) = user.role == Role.ADMIN
    fun canPostAnnouncement(user: Member) = user.role == Role.ADMIN || user.role == Role.TEACHER

    fun teachesCircle(user: Member, circle: Circle?) =
        circle != null && user.role == Role.TEACHER && circle.teacherId == user.id

    fun canRecordFor(user: Member, student: Student, snap: CenterSnapshot): Boolean = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(student.circleId))
        else -> false
    }

    fun canSeeStudent(user: Member, student: Student, snap: CenterSnapshot): Boolean = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(student.circleId))
        Role.STUDENT -> user.studentId == student.id
        Role.PARENT -> student.id in user.childIds
    }

    /** Students this user may see, in a stable order. */
    fun visibleStudents(user: Member, snap: CenterSnapshot): List<Student> =
        snap.students.filter { canSeeStudent(user, it, snap) }.sortedBy { it.fullName }

    fun visibleCircles(user: Member, snap: CenterSnapshot): List<Circle> = when (user.role) {
        Role.ADMIN -> snap.circles
        Role.TEACHER -> snap.circles.filter { it.teacherId == user.id }
        Role.STUDENT -> snap.circles.filter { c -> snap.student(user.studentId)?.circleId == c.id }
        Role.PARENT -> snap.circles.filter { c -> user.childIds.any { snap.student(it)?.circleId == c.id } }
    }.sortedBy { it.name }

    fun audienceIncludes(a: Audience, role: Role) = when (a) {
        Audience.ALL -> true
        Audience.TEACHERS -> role == Role.TEACHER || role == Role.ADMIN
        Audience.PARENTS -> role == Role.PARENT || role == Role.ADMIN
        Audience.STUDENTS -> role == Role.STUDENT || role == Role.ADMIN
    }
}
