package com.alsiddiq.app.data.demo

import com.alsiddiq.app.core.Quran
import com.alsiddiq.app.domain.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Sample data for design and testing only. Never used once the central database is connected. */
object DemoSeed {
    const val VERSION = 3
    const val DEMO_PASSWORD = "123456"
    const val ADMIN_PHONE = "0500000001"
    const val TEACHER_PHONE = "0500000002"
    const val STUDENT_PHONE = "0500000010"
    const val PARENT_PHONE = "0500000020"

    private fun h(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun create(): DemoStore {
        val rnd = Random(7)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val admin = Member("m-admin", "إدارة المركز", ADMIN_PHONE, Role.ADMIN)
        val t1 = Member("m-t1", "الشيخ عبدالله الحربي", TEACHER_PHONE, Role.TEACHER)
        val t2 = Member("m-t2", "الأستاذ خالد العمري", "0500000003", Role.TEACHER)
        val t3 = Member("m-t3", "الأستاذ ياسر القحطاني", "0500000004", Role.TEACHER)

        val circles = listOf(
            Circle("c1", "حلقة أبي بكر الصديق", t1.id, "متقدم", "بعد العصر • السبت–الأربعاء", 15),
            Circle("c2", "حلقة عمر بن الخطاب", t2.id, "متوسط", "بعد العصر • السبت–الأربعاء", 15),
            Circle("c3", "حلقة البراعم", t3.id, "تمهيدي", "بعد المغرب • الأحد–الخميس", 20),
            Circle("c4", "حلقة الفجر للكبار", t1.id, "مراجعة", "بعد الفجر • يومياً", 12)
        )
        val names = listOf(
            "محمد السالمي", "أحمد الزهراني", "عبدالرحمن الشهري", "يوسف الغامدي", "عمر الدوسري",
            "إبراهيم المالكي", "سلمان العتيبي", "فيصل الشمري", "حمزة البقمي", "أنس الحارثي",
            "زياد القرني", "معاذ السبيعي", "بلال المطيري", "عبدالعزيز الجهني", "مالك الرشيدي",
            "سعد الأحمدي", "تركي العنزي", "نواف الخالدي"
        )
        val students = names.mapIndexed { i, n ->
            val circle = circles[i % circles.size]
            Student(
                id = "s${i + 1}", fullName = n, phone = "05000001%02d".format(i),
                birthYear = 2008 + (i % 9), circleId = circle.id,
                joinedOn = today.minusDays(40L + i * 9).toString(),
                goalJuz = if (circle.id == "c3") 3 else if (circle.id == "c4") 30 else 10
            )
        }
        val studentUser = Member("m-s1", students[0].fullName, STUDENT_PHONE, Role.STUDENT, studentId = students[0].id)
        val p1 = Member("m-p1", "والد محمد السالمي", PARENT_PHONE, Role.PARENT, childIds = listOf("s1", "s5"))
        val p2 = Member("m-p2", "والد أحمد الزهراني", "0500000021", Role.PARENT, childIds = listOf("s2"))
        val p3 = Member("m-p3", "والدة يوسف الغامدي", "0500000022", Role.PARENT, childIds = listOf("s4", "s8"))
        val members = listOf(admin, t1, t2, t3, studentUser, p1, p2, p3)

        val recs = mutableListOf<Recitation>()
        val att = mutableListOf<Attendance>()
        val rewards = mutableListOf<Reward>()
        var seq = 0
        students.forEachIndexed { idx, s ->
            val circle = circles.first { it.id == s.circleId }
            // Start position: juniors from An-Nas backwards, others from Al-Baqarah.
            var surah = if (circle.id == "c3") 114 else if (circle.id == "c4") 2 else 1 + (idx % 3)
            var ayah = 1
            for (back in 20 downTo 0) {
                val day = today.minusDays(back.toLong())
                if (day.dayOfWeek.value == 5) continue // Friday off
                val present = rnd.nextInt(100) < 88
                att += Attendance("a${seq++}", s.id, circle.id, day.toString(),
                    if (present) (if (rnd.nextInt(10) == 0) AttendanceStatus.LATE else AttendanceStatus.PRESENT)
                    else if (rnd.nextBoolean()) AttendanceStatus.EXCUSED else AttendanceStatus.ABSENT,
                    circle.teacherId ?: admin.id)
                if (!present) continue
                val at = day.atTime(16 + rnd.nextInt(3), rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                val count = (if (circle.id == "c3") 2 else 4) + rnd.nextInt(5)
                val toAyah = (ayah + count - 1).coerceAtMost(Quran.ayahCount(surah))
                val grade = Grade.entries[listOf(0, 0, 0, 1, 1, 1, 2, 2, 3, 4)[rnd.nextInt(10)]]
                recs += Recitation("r${seq++}", s.id, circle.id, circle.teacherId ?: admin.id, RecordKind.HIFZ,
                    surah, ayah, surah, toAyah, grade, rnd.nextInt(4), "", at)
                if (rnd.nextInt(3) == 0) {
                    val ms = surah
                    recs += Recitation("r${seq++}", s.id, circle.id, circle.teacherId ?: admin.id, RecordKind.MURAJA,
                        ms, 1, ms, toAyah, Grade.entries[rnd.nextInt(3)], rnd.nextInt(3), "", at + 600_000)
                }
                if (grade != Grade.WEAK) {
                    ayah = toAyah + 1
                    if (ayah > Quran.ayahCount(surah)) {
                        surah = if (circle.id == "c3") (surah - 1).coerceAtLeast(78) else (surah + 1).coerceAtMost(114)
                        ayah = 1
                    }
                }
            }
            if (idx % 2 == 0) rewards += Reward("w${seq++}", s.id, 10 + rnd.nextInt(4) * 5, "إتقان الحفظ هذا الأسبوع", circle.teacherId ?: admin.id,
                System.currentTimeMillis() - rnd.nextLong(1, 10) * 86_400_000L)
            if (idx % 3 == 0) rewards += Reward("w${seq++}", s.id, 5, "الانضباط في الحضور", circle.teacherId ?: admin.id,
                System.currentTimeMillis() - rnd.nextLong(1, 15) * 86_400_000L)
        }
        val now = System.currentTimeMillis()
        val announcements = listOf(
            Announcement("n1", "مسابقة الحفظ الشهرية", "تبدأ مسابقة المركز الشهرية يوم الخميس القادم بعد العصر، والجوائز للمراكز الثلاثة الأولى في كل حلقة.", Audience.ALL, admin.id, now - 2 * 86_400_000L),
            Announcement("n2", "اجتماع أولياء الأمور", "نتشرف بحضوركم لقاء أولياء الأمور يوم السبت بعد المغرب في مصلى المركز.", Audience.PARENTS, admin.id, now - 4 * 86_400_000L),
            Announcement("n3", "تحديث خطة المراجعة", "اعتماد مراجعة جزء كامل أسبوعياً لطلاب الحلقات المتقدمة.", Audience.TEACHERS, admin.id, now - 6 * 86_400_000L)
        )
        val notes = listOf(
            AppNotification("x1", p1.id, "حفظ جديد: محمد السالمي", "سورة البقرة • ممتاز", "record", null, false, now - 3_600_000L),
            AppNotification("x2", p1.id, "إعلان: اجتماع أولياء الأمور", "نتشرف بحضوركم لقاء أولياء الأمور يوم السبت بعد المغرب.", "announcement", "n2", false, now - 4 * 86_400_000L),
            AppNotification("x3", studentUser.id, "مكافأة 🎉", "+15 نقطة: إتقان الحفظ هذا الأسبوع", "reward", null, false, now - 86_400_000L),
            AppNotification("x4", t1.id, "إعلان: تحديث خطة المراجعة", "اعتماد مراجعة جزء كامل أسبوعياً.", "announcement", "n3", true, now - 6 * 86_400_000L),
            AppNotification("x5", admin.id, "مرحباً بك في مركز الصديق", "هذه نسخة تجريبية ببيانات نموذجية. ستُربط بقاعدة البيانات المركزية قريباً.", "info", null, false, now)
        )
        val data = CenterSnapshot(
            center = CenterInfo(),
            members = members, circles = circles, students = students,
            recitations = recs, attendance = att, rewards = rewards,
            announcements = announcements, notifications = notes
        )
        val hashes = members.associate { it.id to h(DEMO_PASSWORD) }
        return DemoStore(data, hashes, null, VERSION)
    }
}
