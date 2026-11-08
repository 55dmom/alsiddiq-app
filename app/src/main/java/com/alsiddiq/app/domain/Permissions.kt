package com.alsiddiq.app.domain

/**
 * Client-side mirror of the server's Row Level Security rules (v3.3). The server is the
 * authority; this only decides what the UI offers and what the demo store accepts.
 *  - VIEWER (general password): reads everything, writes nothing.
 *  - TEACHER (v3.3: his OWN password, set by the admin): EDITS only his own circle(s),
 *    VIEWS every circle of the centre read-only. The old v3.1 shared «teachers» account
 *    (allCircles) no longer opens any edit.
 *  - ADMIN (admin password): everything.
 */
object Permissions {
    fun canManageCenter(user: Member) = user.role == Role.ADMIN
    fun canManageMembers(user: Member) = user.role == Role.ADMIN
    fun canManageCircles(user: Member) = user.role == Role.ADMIN
    fun canManageStudents(user: Member) = user.role == Role.ADMIN
    fun canPostAnnouncement(user: Member) = user.role == Role.ADMIN || (user.role == Role.TEACHER && !user.allCircles)
    fun isReadOnly(user: Member) = user.role == Role.VIEWER || (user.role == Role.TEACHER && user.allCircles)

    /** v3.3: own circle only (the shared account owns none). */
    fun teachesCircle(user: Member, circle: Circle?) =
        circle != null && user.role == Role.TEACHER && !user.allCircles && circle.taughtBy(user.id)

    /** Add a student into this circle (admin anywhere; teacher only into his own circle). */
    fun canAddStudentTo(user: Member, circleId: String?, snap: CenterSnapshot) = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(circleId))
        Role.VIEWER -> false
    }

    fun canEditStudent(user: Member, student: Student, snap: CenterSnapshot) = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(student.circleId))
        Role.VIEWER -> false
    }

    /** v3.6 «فصل الطالب»: the admin anywhere, a teacher only in his own circle. */
    fun canDismissStudent(user: Member, student: Student, snap: CenterSnapshot) = student.active && when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(student.circleId))
        Role.VIEWER -> false
    }
    /** v3.6: only the admin reads the teachers' actions. */
    fun canSeeEvents(user: Member) = user.role == Role.ADMIN

    fun canRecordFor(user: Member, student: Student, snap: CenterSnapshot): Boolean = when (user.role) {
        Role.ADMIN -> true
        Role.TEACHER -> teachesCircle(user, snap.circle(student.circleId))
        Role.VIEWER -> false
    }

    /** v3.3: everyone signed in reads every student of the centre (teachers read-only outside their circle). */
    fun canSeeStudent(user: Member, student: Student, snap: CenterSnapshot): Boolean = true

    /** Students this user may see, in a stable order. */
    fun visibleStudents(user: Member, snap: CenterSnapshot): List<Student> =
        snap.students.filter { canSeeStudent(user, it, snap) }.sortedBy { it.fullName }

    /** Every circle the user may LOOK at (v3.3: all of them, for everyone). */
    fun visibleCircles(user: Member, snap: CenterSnapshot): List<Circle> = snap.circles.sortedBy { it.name }

    /** v3.3: the circles the user may WRITE in: admin all, a teacher his own, the viewer none. */
    fun ownCircles(user: Member, snap: CenterSnapshot): List<Circle> = when (user.role) {
        Role.ADMIN -> snap.circles
        Role.TEACHER -> snap.circles.filter { teachesCircle(user, it) }
        Role.VIEWER -> emptyList()
    }.sortedBy { it.name }

    /** v3.3: the students in the user's own circles (admin: all). */
    fun ownStudents(user: Member, snap: CenterSnapshot): List<Student> = when (user.role) {
        Role.ADMIN -> snap.students
        Role.TEACHER -> { val ids = ownCircles(user, snap).map { it.id }.toSet(); snap.students.filter { it.circleId in ids } }
        Role.VIEWER -> emptyList()
    }.sortedBy { it.fullName }

    fun audienceIncludes(a: Audience, role: Role) = when (a) {
        Audience.ALL -> true
        Audience.TEACHERS -> role == Role.TEACHER || role == Role.ADMIN
        Audience.PARENTS, Audience.STUDENTS -> role == Role.ADMIN
    }
}
