package com.alsiddiq.app

import com.alsiddiq.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private val admin = Member("a", "Admin", "1", Role.ADMIN)
    private val teacher = Member("t", "T", "2", Role.TEACHER)
    private val other = Member("t2", "T2", "3", Role.TEACHER)
    private val parent = Member("p", "P", "4", Role.PARENT, childIds = listOf("s1"))
    private val studentUser = Member("su", "S", "5", Role.STUDENT, studentId = "s2")
    private val c1 = Circle("c1", "C1", "t")
    private val c2 = Circle("c2", "C2", "t2")
    private val s1 = Student("s1", "S1", circleId = "c1", joinedOn = "2026-01-01")
    private val s2 = Student("s2", "S2", circleId = "c2", joinedOn = "2026-01-01")
    private val snap = CenterSnapshot(members = listOf(admin, teacher, other, parent, studentUser), circles = listOf(c1, c2), students = listOf(s1, s2))

    @Test fun permissions() {
        assertTrue(Permissions.canRecordFor(admin, s1, snap))
        assertTrue(Permissions.canRecordFor(teacher, s1, snap))
        assertFalse(Permissions.canRecordFor(teacher, s2, snap))
        assertFalse(Permissions.canRecordFor(parent, s1, snap))
        assertEquals(listOf("s1"), Permissions.visibleStudents(parent, snap).map { it.id })
        assertEquals(listOf("s2"), Permissions.visibleStudents(studentUser, snap).map { it.id })
        assertEquals(listOf("c1"), Permissions.visibleCircles(teacher, snap).map { it.id })
        assertFalse(Permissions.canManageStudents(teacher))
    }

    @Test fun memorisedCountsUnionNotSum() {
        val now = System.currentTimeMillis()
        val recs = listOf(
            Recitation("r1", "s1", "c1", "t", RecordKind.HIFZ, 2, 1, 2, 10, Grade.EXCELLENT, recordedAt = now),
            Recitation("r2", "s1", "c1", "t", RecordKind.HIFZ, 2, 5, 2, 15, Grade.GOOD, recordedAt = now),
            Recitation("r3", "s1", "c1", "t", RecordKind.MURAJA, 1, 1, 1, 7, Grade.GOOD, recordedAt = now),
            Recitation("r4", "s1", "c1", "t", RecordKind.HIFZ, 3, 1, 3, 9, Grade.WEAK, recordedAt = now)
        )
        val st = Stats.forStudent(s1, snap.copy(recitations = recs))
        assertEquals(15, st.memorisedAyat) // 2:1–15; revision and weak attempts don't count
        assertEquals(1, st.streakDays)
    }
}
