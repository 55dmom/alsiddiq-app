package com.alsiddiq.app.domain

import com.alsiddiq.app.core.Quran
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StudentStats(
    val memorisedAyat: Int,
    val memorisedPages: Double,
    val juzEquivalent: Double,
    val goalProgress: Float,
    val recordsLast30: Int,
    val hifzAyatLast30: Int,
    val murajaAyatLast30: Int,
    val averageGrade: Double,
    val attendanceRate: Float,
    val presentDays: Int,
    val absentDays: Int,
    val points: Int,
    val streakDays: Int,
    val lastRecord: Recitation?,
    /** Ayat recorded per day for the last 14 days, oldest first. */
    val dailyAyat: List<Pair<LocalDate, Int>>
)

object Stats {
    val zone: ZoneId = ZoneId.systemDefault()

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun ayatOf(r: Recitation) = Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah)

    fun pagesOf(r: Recitation): Double {
        if (!Quran.isValid(r.fromSurah, r.fromAyah) || !Quran.isValid(r.toSurah, r.toAyah)) return 0.0
        return Quran.pagesOf(Quran.globalIndex(r.fromSurah, r.fromAyah), Quran.globalIndex(r.toSurah, r.toAyah))
    }

    fun memorisedMask(records: List<Recitation>): BooleanArray {
        val mask = BooleanArray(Quran.TOTAL_AYAT + 1)
        records.filter { it.kind == RecordKind.HIFZ && it.grade != Grade.WEAK }.forEach { r ->
            if (Quran.isValid(r.fromSurah, r.fromAyah) && Quran.isValid(r.toSurah, r.toAyah)) {
                val a = Quran.globalIndex(r.fromSurah, r.fromAyah)
                val b = Quran.globalIndex(r.toSurah, r.toAyah)
                for (i in a..b) mask[i] = true
            }
        }
        return mask
    }

    fun forStudent(student: Student, snap: CenterSnapshot, today: LocalDate = LocalDate.now()): StudentStats {
        val recs = snap.recitations.filter { it.studentId == student.id }.sortedByDescending { it.recordedAt }
        val mask = memorisedMask(recs)
        val ayat = (1..Quran.TOTAL_AYAT).count { mask[it] }
        val pages = Quran.pagesForMask(mask)
        val juz = Quran.juzEquivalent(pages)
        val since = today.minusDays(29)
        val last30 = recs.filter { !dayOf(it.recordedAt).isBefore(since) }
        val att = snap.attendance.filter { it.studentId == student.id }
        val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val absent = att.count { it.status == AttendanceStatus.ABSENT }
        val counted = att.count { it.status != AttendanceStatus.EXCUSED }
        val points = snap.rewards.filter { it.studentId == student.id }.sumOf { it.points }
        val days = recs.map { dayOf(it.recordedAt) }.toSet()
        var streak = 0
        var d = if (today in days) today else today.minusDays(1)
        while (d in days) { streak++; d = d.minusDays(1) }
        val daily = (13 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            day to recs.filter { dayOf(it.recordedAt) == day }.sumOf { ayatOf(it) }
        }
        return StudentStats(
            memorisedAyat = ayat,
            memorisedPages = pages,
            juzEquivalent = juz,
            goalProgress = if (student.goalJuz > 0) (juz / student.goalJuz).toFloat().coerceIn(0f, 1f) else 0f,
            recordsLast30 = last30.size,
            hifzAyatLast30 = last30.filter { it.kind == RecordKind.HIFZ }.sumOf { ayatOf(it) },
            murajaAyatLast30 = last30.filter { it.kind != RecordKind.HIFZ }.sumOf { ayatOf(it) },
            averageGrade = if (recs.isEmpty()) 0.0 else recs.map { it.grade.score }.average(),
            attendanceRate = if (counted == 0) 0f else present.toFloat() / counted,
            presentDays = present,
            absentDays = absent,
            points = points,
            streakDays = streak,
            lastRecord = recs.firstOrNull(),
            dailyAyat = daily
        )
    }

    data class CenterStats(
        val students: Int,
        val circles: Int,
        val teachers: Int,
        val recordsToday: Int,
        val attendanceToday: Float,
        val ayatThisWeek: Int,
        val gradeDistribution: Map<Grade, Int>,
        val dailyRecords: List<Pair<LocalDate, Int>>,
        val kindSplit: Map<RecordKind, Int>
    )

    fun forCenter(snap: CenterSnapshot, students: List<Student>, today: LocalDate = LocalDate.now()): CenterStats {
        val ids = students.map { it.id }.toSet()
        val recs = snap.recitations.filter { it.studentId in ids }
        val todayIso = today.toString()
        val attToday = snap.attendance.filter { it.studentId in ids && it.day == todayIso }
        val presentToday = attToday.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val weekStart = today.minusDays(6)
        return CenterStats(
            students = students.size,
            circles = snap.circles.count { c -> students.any { it.circleId == c.id } },
            teachers = snap.teacherCount(),
            recordsToday = recs.count { dayOf(it.recordedAt) == today },
            attendanceToday = if (attToday.isEmpty()) 0f else presentToday.toFloat() / attToday.size,
            ayatThisWeek = recs.filter { !dayOf(it.recordedAt).isBefore(weekStart) }.sumOf { ayatOf(it) },
            gradeDistribution = Grade.entries.associateWith { g -> recs.count { it.grade == g } },
            dailyRecords = (13 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                day to recs.count { dayOf(it.recordedAt) == day }
            },
            kindSplit = RecordKind.entries.associateWith { k -> recs.filter { it.kind == k }.sumOf { ayatOf(it) } }
        )
    }

    /** «المتفوقون»: one student's results over a period, from the same records the reports use. */
    data class Honor(val student: Student, val hifzPages: Double, val ayat: Int, val avgGrade: Double, val attendance: Float, val records: Int, val score: Double)

    /**
     * Ranks students for «المتفوقون» over [since] (null = all time), on the phone, from the synced data:
     * new memorisation (pages, as in the reports) first, then the average grade and attendance. Students with nothing recorded are left out.
     */
    fun honors(snap: CenterSnapshot, students: List<Student>, since: LocalDate?): List<Honor> = students.mapNotNull { s ->
        val recs = snap.recitations.filter { it.studentId == s.id && (since == null || !dayOf(it.recordedAt).isBefore(since)) }
        if (recs.isEmpty()) return@mapNotNull null
        val hifz = recs.filter { it.kind == RecordKind.HIFZ && it.grade != Grade.WEAK }
        val pages = hifz.sumOf { pagesOf(it) }
        val att = snap.attendance.filter { it.studentId == s.id && (since == null || runCatching { !LocalDate.parse(it.day).isBefore(since) }.getOrDefault(true)) }
        val counted = att.count { it.status != AttendanceStatus.EXCUSED }
        val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val rate = if (counted == 0) 0f else present.toFloat() / counted
        val grade = recs.map { it.grade.score }.average()
        Honor(s, pages, recs.sumOf { ayatOf(it) }, grade, rate, recs.size, pages * 10 + grade * 4 + rate * 10)
    }.sortedWith(compareByDescending<Honor> { it.score }.thenByDescending { it.ayat })

    /** v3.8 «وسام الشرف لهذا الأسبوع»: the week starts on Saturday. */
    fun weekStart(today: LocalDate = LocalDate.now()): LocalDate = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong())

    /**
     * v3.8 «وسام الشرف لهذا الأسبوع» — automatic (his word «تلقائي», Nov 8): the circle's student with the most ayat recorded
     * this week (Saturday → today); ties go to the fewest absences this week, then the better average grade.
     * null = nothing recorded in the circle this week («لم يُحدد بعد»).
     */
    fun weeklyStar(snap: CenterSnapshot, circleId: String, today: LocalDate = LocalDate.now()): Student? {
        val from = weekStart(today)
        val students = snap.studentsIn(circleId).filter { it.active }
        data class Row(val s: Student, val ayat: Int, val absent: Int, val grade: Double)
        return students.mapNotNull { s ->
            val recs = snap.recitations.filter { it.studentId == s.id && !dayOf(it.recordedAt).isBefore(from) && !dayOf(it.recordedAt).isAfter(today) }
            if (recs.isEmpty()) return@mapNotNull null
            val absent = snap.attendance.count { it.studentId == s.id && it.status == AttendanceStatus.ABSENT &&
                runCatching { !LocalDate.parse(it.day).isBefore(from) }.getOrDefault(false) }
            Row(s, recs.sumOf { ayatOf(it) }, absent, recs.map { it.grade.score }.average())
        }.sortedWith(compareByDescending<Row> { it.ayat }.thenBy { it.absent }.thenByDescending { it.grade }.thenBy { it.s.fullName })
            .firstOrNull()?.s
    }

    fun describeRange(r: Recitation): String =
        if (r.fromSurah == r.toSurah) "${Quran.name(r.fromSurah)} ${r.fromAyah}–${r.toAyah}"
        else "${Quran.name(r.fromSurah)} ${r.fromAyah} إلى ${Quran.name(r.toSurah)} ${r.toAyah}"
}
