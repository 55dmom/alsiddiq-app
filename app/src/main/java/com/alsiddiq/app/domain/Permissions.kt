package com.alsiddiq.app.domain

/**
 * Client-side mirror of the server's Row Level Security rules (v3). The server is the
 * authority; this only decides what the UI offers and what the demo store accepts.
 *  - VIEWER (general password): reads everything, writes nothing.
 *  - TEACHER: v3.1 the ONE shared teachers' password (allCircles) reads and writes every student in every circle;
 *    an old per-teacher account (allCircles = false) still only its own circle(s).
 *  - ADMIN (admin password): everything.
 */
object Permissions {
    fun canManageCenter(user: Member) = user.role == Role.ADMIN
    fun canManageMembers(user: Member) = user.role == Role.ADMIN
    fun canManageCircles(user: Member) = user.role == Role.ADMIN
    fun canManageStudents(user: Member) = user.role == Role.ADMIN
    fun canPostAnnouncement(user: Member) = user.role == Role.ADMIN || user.role == Role.TEACHER
    fun isReadOnly(user: Member) = user.role == Role.VIEWER

    fun teachesCircle(user: Member, circle: Circle?) =
        circle != null && user.role == Role.TEACHER && (user.allCircles || circle.teacherId == user.id)

    /** Add a student into this circle (admin anywhere; teacher only into their own circle). */
    fun canAddStudentTo(user: Member, circleId: String?, snap: CenterSnapshot) = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> user.allCircles || teachesCircle(user, snap.circle(circleId))
        Role.VIEWER -> false
    }

    fun canEditStudent(user: Member, student: Student, snap: CenterSnapshot) = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> user.allCircles || teachesCircle(user, snap.circle(student.circleId))
        Role.VIEWER -> false
    }

    fun canRecordFor(user: Member, student: Student, snap: CenterSnapshot): Boolean = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> user.allCircles || teachesCircle(user, snap.circle(student.circleId))
        Role.VIEWER -> false
    }

    fun canSeeStudent(user: Member, student: Student, snap: CenterSnapshot): Boolean = when (user.role) {
        Role.ADMIN, Role.VIEWER -> true
        Role.TEACHER -> user.allCircles || teachesCircle(user, snap.circle(student.circleId))
    }

    /** Students this user may see, in a stable order. */
    fun visibleStudents(user: Member, snap: CenterSnapshot): List<Student> =
        snap.students.filter { canSeeStudent(user, it, snap) }.sortedBy { it.fullName }

    fun visibleCircles(user: Member, snap: CenterSnapshot): List<Circle> = when (user.role) {
        Role.ADMIN, Role.VIEWER -> snap.circles
        Role.TEACHER -> if (user.allCircles) snap.circles else snap.circles.filter { it.teacherId == user.id }
    }.sortedBy { it.name }

    fun audienceIncludes(a: Audience, role: Role) = when (a) {
        Audience.ALL -> true
        Audience.TEACHERS -> role == Role.TEACHER || role == Role.ADMIN
        Audience.PARENTS, Audience.STUDENTS -> role == Role.ADMIN
    }
}
