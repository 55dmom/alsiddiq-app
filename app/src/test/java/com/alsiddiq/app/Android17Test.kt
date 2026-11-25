package com.alsiddiq.app

import com.alsalmi.buttons.tasbeeh.TasbeehLogic
import com.alsiddiq.app.core.Quran
import com.alsiddiq.app.domain.*
import com.alsiddiq.app.ui.tabsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class Android17Test {
    private val zone = ZoneId.systemDefault()
    private val month = YearMonth.of(2026, 11) // Nov 2026: 30 days, 4 Fridays (6, 13, 20, 27) => 26 study days
    private fun millisOn(day: Int) = month.atDay(day).atTime(16, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun gradeFactorsMatchApprovedWeights() {
        assertEquals(1.00, Stats.gradeFactor(Grade.EXCELLENT), 1e-9)
        assertEquals(0.85, Stats.gradeFactor(Grade.VERY_GOOD), 1e-9)
        assertEquals(0.70, Stats.gradeFactor(Grade.GOOD), 1e-9)
        assertEquals(0.55, Stats.gradeFactor(Grade.PASS), 1e-9)
        assertEquals(0.30, Stats.gradeFactor(Grade.WEAK), 1e-9)
    }

    @Test fun activeStudyDaysExcludeFridays() {
        // Nov 2026 has 30 days and 4 Fridays (Nov 6, 13, 20, 27) -> 26 study days
        assertEquals(26, Stats.activeStudyDaysIn(month))
    }

    @Test fun monthlyScoreOutOf500PerfectAndMultipliers() {
        // Juz 1 is roughly Al-Fatiha 1:1 to Al-Baqarah 2:141 (~21 pages, > 1 juz = 20.133 pages)
        val s = Student("s1", "طالب متميز", circleId = "c1", joinedOn = "2026-01-01", monthlyJuzTarget = 1.0)
        val att = (1..30).mapNotNull { d ->
            val ld = month.atDay(d)
            if (RestDay.isRest(ld)) null
            else Attendance("a$d", "s1", "c1", ld.toString(), AttendanceStatus.PRESENT, "t1")
        }
        val recs = listOf(
            Recitation(
                id = "r1", studentId = "s1", circleId = "c1", teacherId = "t1",
                kind = RecordKind.HIFZ, fromSurah = 1, fromAyah = 1, toSurah = 2, toAyah = 141,
                grade = Grade.EXCELLENT, mistakes = 0, recordedAt = millisOn(2),
                reviewMistakes = 1, reviewStatus = "reviewed",
                reviewFromSurah = 1, reviewFromAyah = 1, reviewToSurah = 2, reviewToAyah = 141,
                reviewGrade = Grade.EXCELLENT
            )
        )
        val snap = CenterSnapshot(students = listOf(s), recitations = recs, attendance = att)
        val ms = Stats.monthlyScore(s, snap, month)
        assertEquals(350.0, ms.hifzScore, 1e-6)
        assertEquals(100.0, ms.reviewScore, 1e-6)
        assertEquals(50.0, ms.attendanceScore, 1e-6)
        assertEquals(500.0, ms.totalScore, 1e-6)
        assertEquals(100, ms.percent)
    }

    @Test fun azfarAndNotReviewedScoreZeroAndExcusedIsHalf() {
        val s = Student("s2", "طالب", circleId = "c1", joinedOn = "2026-01-01", monthlyJuzTarget = 0.0) // 0 defaults to 1.0
        val att = listOf(
            Attendance("a1", "s2", "c1", month.atDay(1).toString(), AttendanceStatus.PRESENT, "t1"),  // 1.0
            Attendance("a2", "s2", "c1", month.atDay(2).toString(), AttendanceStatus.EXCUSED, "t1"),  // 0.5
            Attendance("a3", "s2", "c1", month.atDay(3).toString(), AttendanceStatus.ABSENT, "t1")    // 0.0
        )
        val recs = listOf(
            Recitation(
                id = "r1", studentId = "s2", circleId = "c1", teacherId = "t1",
                kind = RecordKind.AZFAR, fromSurah = 2, fromAyah = 1, toSurah = 2, toAyah = 50,
                grade = Grade.EXCELLENT, mistakes = 2, recordedAt = millisOn(2),
                reviewMistakes = 0, reviewStatus = "not_reviewed"
            )
        )
        val snap = CenterSnapshot(students = listOf(s), recitations = recs, attendance = att)
        val ms = Stats.monthlyScore(s, snap, month)
        assertEquals(1.0, ms.monthlyJuzTarget, 1e-9)
        assertEquals(0.0, ms.hifzScore, 1e-6)
        assertEquals(0.0, ms.reviewScore, 1e-6)
        val expectedAtt = (1.5 / 26.0) * 50.0
        assertEquals(expectedAtt, ms.attendanceScore, 1e-6)
        assertEquals(expectedAtt, ms.totalScore, 1e-6)
    }

    @Test fun independentErrorCountersPreservedSeparately() {
        val rec = Recitation(
            id = "r1", studentId = "s1", circleId = "c1", teacherId = "t1",
            kind = RecordKind.SARD, fromSurah = 78, fromAyah = 1, toSurah = 78, toAyah = 40,
            grade = Grade.VERY_GOOD, mistakes = 2, recordedAt = millisOn(5),
            reviewMistakes = 5, reviewStatus = "reviewed",
            reviewFromSurah = 79, reviewFromAyah = 1, reviewToSurah = 79, reviewToAyah = 20,
            reviewGrade = Grade.GOOD
        )
        assertEquals(2, rec.mistakes)
        assertEquals(5, rec.reviewMistakes)
        assertEquals("النازعات 1–20", Stats.describeReviewRange(rec))
    }

    @Test fun deletedTeacherDisappearsFromTeachersListWhileRecordsRemain() {
        val tActive = Member("t1", "معلم نشط", "", Role.TEACHER, active = true, deletedAt = null)
        val tDeleted = Member("t2", "معلم محذوف", "", Role.TEACHER, active = false, deletedAt = 123456L)
        val circle = Circle("c1", "حلقة الصديق", teacherId = null)
        val rec = Recitation("r1", "s1", "c1", "t2", RecordKind.HIFZ, 1, 1, 1, 7, Grade.EXCELLENT)
        val snap = CenterSnapshot(members = listOf(tActive, tDeleted), circles = listOf(circle), recitations = listOf(rec))
        assertEquals(listOf("t1"), snap.teachers().map { it.id })
        assertEquals(1, snap.teacherCount())
        assertEquals(1, snap.circles.size)
        assertEquals("معلم محذوف", snap.recorder(null, "t2"))
    }

    @Test fun viewerHasReportsTabAndIsReadOnly() {
        val viewer = Member("v1", "العامة", "", Role.VIEWER)
        val s = Student("s1", "طالب", circleId = "c1", joinedOn = "2026-01-01")
        val snap = CenterSnapshot(students = listOf(s))
        assertTrue(tabsFor(Role.VIEWER).any { it.route == "reports" })
        assertTrue(Permissions.isReadOnly(viewer))
        assertFalse(Permissions.canRecordFor(viewer, s, snap))
        assertFalse(Permissions.canEditStudent(viewer, s, snap))
        assertFalse(Permissions.canManageMembers(viewer))
    }

    @Test fun tasbeehAutoAdvancesAt33AndWrapsLastToFirstAt0() {
        val totalPhrases = 9
        // Taps 1..32 stay on dhikr 0
        val mid = TasbeehLogic.step(currentIndex = 0, currentCount = 31, target = 33, phraseCount = totalPhrases)
        assertFalse(mid.reached)
        assertEquals(0, mid.nextIndex)
        assertEquals(32, mid.nextCount)

        // Tap 33 on dhikr 0 -> advances to dhikr 1 at count 0
        val next = TasbeehLogic.step(currentIndex = 0, currentCount = 32, target = 33, phraseCount = totalPhrases)
        assertTrue(next.reached)
        assertEquals(1, next.nextIndex)
        assertEquals(0, next.nextCount)

        // Tap 33 on last dhikr (index 8) -> wraps to first dhikr (index 0) at count 0
        val wrap = TasbeehLogic.step(currentIndex = 8, currentCount = 32, target = 33, phraseCount = totalPhrases)
        assertTrue(wrap.reached)
        assertEquals(0, wrap.nextIndex)
        assertEquals(0, wrap.nextCount)
    }
}
