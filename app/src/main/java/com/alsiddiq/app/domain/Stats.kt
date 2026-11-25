package com.alsiddiq.app.domain

import com.alsiddiq.app.core.Quran
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Build 17 monthly scoring system (out of 500 points):
 *  - الحفظ والتسميع والسرد: 350 درجة (distributed across student's monthly juz target * rating factor; لم يسمع = 0%)
 *  - المراجعة: 100 درجة (same rating factors; لم يراجع = 0%)
 *  - الحضور: 50 درجة (distributed across active study days in the month, Friday excluded: حاضر 100%, مستأذن 50%, غائب 0%)
 *  - Percentage = totalScore / 500, capped at 500 (100%).
 */
data class MonthlyScore(
    val month: YearMonth,
    val monthlyJuzTarget: Double,
    val hifzScore: Double,
    val reviewScore: Double,
    val attendanceScore: Double,
    val totalScore: Double,
    val percent: Int,
    val hifzPages: Double,
    val reviewPages: Double,
    val presentDays: Int,
    val excusedDays: Int,
    val absentDays: Int,
    val activeStudyDays: Int
) {
    val totalRounded: Int get() = totalScore.roundToInt().coerceIn(0, 500)
    val hifzRounded: Int get() = hifzScore.roundToInt().coerceIn(0, 350)
    val reviewRounded: Int get() = reviewScore.roundToInt().coerceIn(0, 100)
    val attendanceRounded: Int get() = attendanceScore.roundToInt().coerceIn(0, 50)
    fun summaryText(): String = "$totalRounded / 500 · $percent%"
}

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
    val dailyAyat: List<Pair<LocalDate, Int>>,
    /** Build 17: current month's 500-point grade breakdown. */
    val monthlyScore: MonthlyScore
)

object Stats {
    val zone: ZoneId = ZoneId.systemDefault()
    const val PAGES_PER_JUZ: Double = Quran.TOTAL_PAGES / 30.0
    const val MAX_HIFZ_SCORE: Double = 350.0
    const val MAX_REVIEW_SCORE: Double = 100.0
    const val MAX_ATTENDANCE_SCORE: Double = 50.0
    const val MAX_TOTAL_SCORE: Double = 500.0

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun ayatOf(r: Recitation) = Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah)

    fun pagesOf(r: Recitation): Double {
        if (!Quran.isValid(r.fromSurah, r.fromAyah) || !Quran.isValid(r.toSurah, r.toAyah)) return 0.0
        return Quran.pagesOf(Quran.globalIndex(r.fromSurah, r.fromAyah), Quran.globalIndex(r.toSurah, r.toAyah))
    }

    /** Pages covered by the optional مراجعة box on a recitation record (0.0 if none or لم يراجع). */
    fun reviewPagesOf(r: Recitation): Double {
        if (r.reviewStatus != "reviewed") return 0.0
        val fs = r.reviewFromSurah ?: return 0.0
        val fa = r.reviewFromAyah ?: return 0.0
        val ts = r.reviewToSurah ?: return 0.0
        val ta = r.reviewToAyah ?: return 0.0
        if (!Quran.isValid(fs, fa) || !Quran.isValid(ts, ta)) return 0.0
        if (Quran.ayatBetween(fs, fa, ts, ta) <= 0) return 0.0
        return Quran.pagesOf(Quran.globalIndex(fs, fa), Quran.globalIndex(ts, ta))
    }

    fun reviewAyatOf(r: Recitation): Int {
        if (r.reviewStatus != "reviewed") return 0
        val fs = r.reviewFromSurah ?: return 0
        val fa = r.reviewFromAyah ?: return 0
        val ts = r.reviewToSurah ?: return 0
        val ta = r.reviewToAyah ?: return 0
        return Quran.ayatBetween(fs, fa, ts, ta)
    }

    fun describeReviewRange(r: Recitation): String? {
        if (r.reviewStatus != "reviewed") return null
        val fs = r.reviewFromSurah ?: return null
        val fa = r.reviewFromAyah ?: return null
        val ts = r.reviewToSurah ?: return null
        val ta = r.reviewToAyah ?: return null
        return if (fs == ts) "${Quran.name(fs)} $fa–$ta"
        else "${Quran.name(fs)} $fa إلى ${Quran.name(ts)} $ta"
    }

    /** Approved build 17 rating multipliers: ممتاز 100%, جيد جداً 85%, جيد 70%, مقبول 55%, إعادة 30%. */
    fun gradeFactor(g: Grade): Double = when (g) {
        Grade.EXCELLENT -> 1.00
        Grade.VERY_GOOD -> 0.85
        Grade.GOOD -> 0.70
        Grade.PASS -> 0.55
        Grade.WEAK -> 0.30
    }

    /** Number of active study days in [month] (Friday is a rest day and excluded). */
    fun activeStudyDaysIn(month: YearMonth): Int =
        (1..month.lengthOfMonth()).count { !RestDay.isRest(month.atDay(it)) }.coerceAtLeast(1)

    /**
     * Build 17 monthly score (out of 500) for [student] in [month]:
     *  - الحفظ والتسميع والسرد (350): HIFZ, TASMEE, SARD weighted by gradeFactor over targetPages; AZFAR = 0%.
     *  - المراجعة (100): MURAJA records + optional مراجعة box (when reviewed) weighted by gradeFactor over targetPages; لم يراجع = 0%.
     *  - الحضور (50): distributed across activeStudyDaysIn(month); PRESENT/LATE = 100%, EXCUSED = 50%, ABSENT = 0%.
     */
    fun monthlyScore(student: Student, snap: CenterSnapshot, month: YearMonth = YearMonth.now()): MonthlyScore {
        val targetJuz = if (student.monthlyJuzTarget > 0.0) student.monthlyJuzTarget else 1.0
        val targetPages = (targetJuz * PAGES_PER_JUZ).coerceAtLeast(1.0)

        val monthRecs = snap.recitations.filter {
            it.studentId == student.id && YearMonth.from(dayOf(it.recordedAt)) == month
        }

        // 1) الحفظ والتسميع والسرد (350) — لم يسمع (AZFAR) contributes 0%
        var hifzPagesRaw = 0.0
        var hifzWeightedPages = 0.0
        monthRecs.forEach { r ->
            if (r.kind == RecordKind.HIFZ || r.kind == RecordKind.TASMEE || r.kind == RecordKind.SARD) {
                val p = pagesOf(r)
                if (p > 0.0) {
                    hifzPagesRaw += p
                    hifzWeightedPages += p * gradeFactor(r.grade)
                }
            }
        }
        val hifzScore = ((hifzWeightedPages / targetPages) * MAX_HIFZ_SCORE).coerceIn(0.0, MAX_HIFZ_SCORE)

        // 2) المراجعة (100) — MURAJA records + optional مراجعة box (لم يراجع = 0%)
        var reviewPagesRaw = 0.0
        var reviewWeightedPages = 0.0
        monthRecs.forEach { r ->
            if (r.kind == RecordKind.MURAJA) {
                val p = pagesOf(r)
                if (p > 0.0) {
                    reviewPagesRaw += p
                    reviewWeightedPages += p * gradeFactor(r.grade)
                }
            }
            if (r.reviewStatus == "reviewed") {
                val rp = reviewPagesOf(r)
                if (rp > 0.0) {
                    reviewPagesRaw += rp
                    reviewWeightedPages += rp * gradeFactor(r.reviewGrade ?: r.grade)
                }
            }
        }
        val reviewScore = ((reviewWeightedPages / targetPages) * MAX_REVIEW_SCORE).coerceIn(0.0, MAX_REVIEW_SCORE)

        // 3) الحضور (50) — distributed across active study days (Friday excluded)
        val studyDays = activeStudyDaysIn(month)
        val monthAtt = snap.attendance.filter { a ->
            a.studentId == student.id && runCatching {
                val d = LocalDate.parse(a.day)
                YearMonth.from(d) == month && !RestDay.isRest(d)
            }.getOrDefault(false)
        }
        val presentDays = monthAtt.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val excusedDays = monthAtt.count { it.status == AttendanceStatus.EXCUSED }
        val absentDays = monthAtt.count { it.status == AttendanceStatus.ABSENT }
        val earnedUnits = presentDays * 1.0 + excusedDays * 0.5
        val attendanceScore = ((earnedUnits / studyDays.toDouble()) * MAX_ATTENDANCE_SCORE).coerceIn(0.0, MAX_ATTENDANCE_SCORE)

        val total = (hifzScore + reviewScore + attendanceScore).coerceIn(0.0, MAX_TOTAL_SCORE)
        val pct = ((total / MAX_TOTAL_SCORE) * 100.0).roundToInt().coerceIn(0, 100)

        return MonthlyScore(
            month = month,
            monthlyJuzTarget = targetJuz,
            hifzScore = hifzScore,
            reviewScore = reviewScore,
            attendanceScore = attendanceScore,
            totalScore = total,
            percent = pct,
            hifzPages = hifzPagesRaw,
            reviewPages = reviewPagesRaw,
            presentDays = presentDays,
            excusedDays = excusedDays,
            absentDays = absentDays,
            activeStudyDays = studyDays
        )
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
        val mScore = monthlyScore(student, snap, YearMonth.from(today))
        return StudentStats(
            memorisedAyat = ayat,
            memorisedPages = pages,
            juzEquivalent = juz,
            goalProgress = if (student.goalJuz > 0) (juz / student.goalJuz).toFloat().coerceIn(0f, 1f) else 0f,
            recordsLast30 = last30.size,
            hifzAyatLast30 = last30.filter { it.kind == RecordKind.HIFZ }.sumOf { ayatOf(it) },
            murajaAyatLast30 = last30.filter { it.kind != RecordKind.HIFZ }.sumOf { ayatOf(it) } + last30.sumOf { reviewAyatOf(it) },
            averageGrade = if (recs.isEmpty()) 0.0 else recs.map { it.grade.score }.average(),
            attendanceRate = if (counted == 0) 0f else present.toFloat() / counted,
            presentDays = present,
            absentDays = absent,
            points = points,
            streakDays = streak,
            lastRecord = recs.firstOrNull(),
            dailyAyat = daily,
            monthlyScore = mScore
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
    data class Honor(
        val student: Student,
        val hifzPages: Double,
        val ayat: Int,
        val avgGrade: Double,
        val attendance: Float,
        val records: Int,
        val score: Double,
        val monthlyScore: MonthlyScore
    )

    /**
     * Ranks students for «المتفوقون» over [since] (null = all time), on the phone, from the synced data.
     * For the current month uses the 500-point grading system; for longer periods combines pages, grade, and attendance.
     */
    fun honors(snap: CenterSnapshot, students: List<Student>, since: LocalDate?, today: LocalDate = LocalDate.now()): List<Honor> {
        val thisMonthStart = today.withDayOfMonth(1)
        val useMonthly = since == thisMonthStart
        return students.mapNotNull { s ->
            val recs = snap.recitations.filter { it.studentId == s.id && (since == null || !dayOf(it.recordedAt).isBefore(since)) }
            val ms = monthlyScore(s, snap, YearMonth.from(today))
            if (recs.isEmpty() && (!useMonthly || ms.totalScore <= 0.0)) return@mapNotNull null
            val hifz = recs.filter { (it.kind == RecordKind.HIFZ || it.kind == RecordKind.TASMEE || it.kind == RecordKind.SARD) && it.grade != Grade.WEAK }
            val pages = hifz.sumOf { pagesOf(it) }
            val att = snap.attendance.filter { it.studentId == s.id && (since == null || runCatching { !LocalDate.parse(it.day).isBefore(since) }.getOrDefault(true)) }
            val counted = att.count { it.status != AttendanceStatus.EXCUSED }
            val present = att.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
            val rate = if (counted == 0) 0f else present.toFloat() / counted
            val grade = if (recs.isEmpty()) 0.0 else recs.map { it.grade.score }.average()
            val rankScore = if (useMonthly) ms.totalScore else (pages * 10 + grade * 4 + rate * 10)
            Honor(s, pages, recs.sumOf { ayatOf(it) }, grade, rate, recs.size, rankScore, ms)
        }.sortedWith(compareByDescending<Honor> { it.score }.thenByDescending { it.ayat })
    }

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
