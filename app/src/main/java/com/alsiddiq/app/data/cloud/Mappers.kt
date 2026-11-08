package com.alsiddiq.app.data.cloud

import com.alsiddiq.app.domain.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.OffsetDateTime

/** Converts between database rows (snake_case, lowercase enums) and app models. */
internal object Rows {
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    fun ts(v: String?): Long = v?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
    fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()
    private inline fun <reified E : Enum<E>> enumOf(v: String?, d: E): E =
        v?.let { s -> enumValues<E>().firstOrNull { it.name.equals(s, ignoreCase = true) } } ?: d
    fun db(e: Enum<*>) = e.name.lowercase()

    fun center(o: JsonObject) = CenterInfo(o.s("name") ?: "", o.s("city") ?: "")

    fun member(o: JsonObject) = Member(
        id = o.s("id")!!, fullName = o.s("full_name") ?: "", phone = o.s("phone") ?: "",
        role = enumOf(o.s("role"), Role.VIEWER), active = o.b("active") ?: true, createdAt = ts(o.s("created_at")),
        allCircles = o.s("access_kind") == "teachers"
    )

    fun circle(o: JsonObject) = Circle(
        id = o.s("id")!!, name = o.s("name") ?: "", teacherId = o.s("teacher_id"), level = o.s("level") ?: "",
        schedule = o.s("schedule") ?: "", capacity = o.i("capacity") ?: 15, active = o.b("active") ?: true, createdAt = ts(o.s("created_at")),
        teacherName = o.s("teacher_name"),
        feeYer = o.i("fee_yer"), feePeriod = o.s("fee_period"), photoPath = o.s("photo_path")
    )
    fun circleRow(c: Circle, center: String) = buildJsonObject {
        put("id", c.id); put("center_id", center); put("name", c.name); put("teacher_id", c.teacherId)
        put("teacher_name", c.teacherName)
        put("fee_yer", c.feeYer); put("fee_period", c.feePeriod ?: "monthly")
        put("level", c.level); put("schedule", c.schedule); put("capacity", c.capacity); put("active", c.active)
        put("updated_at", iso(System.currentTimeMillis()))
    }

    fun student(o: JsonObject) = Student(
        id = o.s("id")!!, fullName = o.s("full_name") ?: "", phone = o.s("phone") ?: "", birthYear = o.i("birth_year"),
        circleId = o.s("circle_id"), joinedOn = o.s("joined_on") ?: "", goalJuz = o.i("goal_juz") ?: 5,
        status = enumOf(o.s("status"), StudentStatus.ACTIVE), notes = o.s("notes") ?: "", createdAt = ts(o.s("created_at")),
        photoPath = o.s("photo_path"),
        active = o.b("active") ?: true, dismissedAt = o.s("dismissed_at")?.let { ts(it) }, dismissedBy = o.s("dismissed_by")
    )

    /** «المتفوقون» honor_cards row (students is a jsonb array). */
    fun honorCard(o: JsonObject): HonorCard {
        val arr = o["students"] as? JsonArray ?: JsonArray(emptyList())
        return HonorCard(
            id = o.s("id")!!, message = o.s("message") ?: "", publishedBy = o.s("published_by"), createdAt = ts(o.s("created_at")),
            students = arr.mapNotNull { e -> (e as? JsonObject)?.let { j -> HonorStudent(j.s("id") ?: "", j.s("full_name") ?: "", j.s("photo_path"), j.s("circle_name") ?: "", j.s("score_text") ?: "") } }
        )
    }
    fun honorCardRow(students: List<HonorStudent>, message: String, center: String, by: String) = buildJsonObject {
        put("center_id", center); put("message", message.ifBlank { null })
        put("students", JsonArray(students.map { h -> buildJsonObject { put("id", h.id); put("full_name", h.fullName); put("photo_path", h.photoPath); put("circle_name", h.circleName); put("score_text", h.scoreText) } }))
    }

    /** v3.6 admin_events row (the student snapshot is jsonb). */
    fun event(o: JsonObject): AdminEvent {
        val st = o["student"] as? JsonObject ?: JsonObject(emptyMap())
        return AdminEvent(
            id = o.s("id")!!, kind = o.s("kind") ?: "", actorId = o.s("actor_profile_id"), actorName = o.s("actor_name") ?: "",
            studentId = o.s("student_id"), studentName = st.s("full_name") ?: "", circleName = st.s("circle_name") ?: "",
            circleId = o.s("circle_id"), phone = st.s("phone") ?: "", createdAt = ts(o.s("created_at")), readAt = o.s("read_at")?.let { ts(it) }
        )
    }
    fun studentRow(s: Student, center: String) = buildJsonObject {
        put("id", s.id); put("center_id", center); put("full_name", s.fullName); put("phone", s.phone)
        put("birth_year", s.birthYear); put("circle_id", s.circleId); put("joined_on", s.joinedOn.ifBlank { java.time.LocalDate.now().toString() })
        put("goal_juz", s.goalJuz); put("status", db(s.status)); put("notes", s.notes); put("updated_at", iso(System.currentTimeMillis()))
        put("photo_path", s.photoPath)
    }

    fun recitation(o: JsonObject) = Recitation(
        id = o.s("id")!!, studentId = o.s("student_id")!!, circleId = o.s("circle_id"), teacherId = o.s("teacher_id") ?: "",
        kind = enumOf(o.s("kind"), RecordKind.HIFZ), fromSurah = o.i("from_surah") ?: 1, fromAyah = o.i("from_ayah") ?: 1,
        toSurah = o.i("to_surah") ?: 1, toAyah = o.i("to_ayah") ?: 1, grade = enumOf(o.s("grade"), Grade.GOOD),
        mistakes = o.i("mistakes") ?: 0, notes = o.s("notes") ?: "", recordedAt = ts(o.s("recorded_at")), byName = o.s("by_name")
    )
    fun recitationRow(r: Recitation, center: String) = buildJsonObject {
        put("id", r.id); put("center_id", center); put("student_id", r.studentId); put("circle_id", r.circleId)
        put("teacher_id", r.teacherId); put("kind", db(r.kind)); put("from_surah", r.fromSurah); put("from_ayah", r.fromAyah)
        put("to_surah", r.toSurah); put("to_ayah", r.toAyah); put("grade", db(r.grade)); put("mistakes", r.mistakes)
        put("notes", r.notes); put("recorded_at", iso(r.recordedAt)); put("by_name", r.byName)
    }

    fun attendance(o: JsonObject) = Attendance(
        id = o.s("id")!!, studentId = o.s("student_id")!!, circleId = o.s("circle_id"), day = o.s("day") ?: "",
        status = enumOf(o.s("status"), AttendanceStatus.PRESENT), recordedBy = o.s("recorded_by") ?: "", recordedAt = ts(o.s("recorded_at")),
        byName = o.s("by_name")
    )

    fun reward(o: JsonObject) = Reward(
        id = o.s("id")!!, studentId = o.s("student_id")!!, points = o.i("points") ?: 0, reason = o.s("reason") ?: "",
        awardedBy = o.s("awarded_by") ?: "", createdAt = ts(o.s("created_at")), byName = o.s("by_name")
    )
    fun rewardRow(r: Reward, center: String) = buildJsonObject {
        put("id", r.id); put("center_id", center); put("student_id", r.studentId); put("points", r.points)
        put("reason", r.reason); put("awarded_by", r.awardedBy); put("created_at", iso(r.createdAt)); put("by_name", r.byName)
    }

    fun announcement(o: JsonObject) = Announcement(
        id = o.s("id")!!, title = o.s("title") ?: "", body = o.s("body") ?: "", audience = enumOf(o.s("audience"), Audience.ALL),
        createdBy = o.s("created_by") ?: "", createdAt = ts(o.s("created_at")), byName = o.s("by_name"), imagePath = o.s("image_path")
    )
    fun announcementRow(a: Announcement, center: String) = buildJsonObject {
        put("id", a.id); put("center_id", center); put("title", a.title); put("body", a.body)
        put("audience", db(a.audience)); put("created_by", a.createdBy); put("created_at", iso(a.createdAt)); put("by_name", a.byName)
        a.imagePath?.let { put("image_path", it) }   // v3.8 (only sent when there is a photo)
    }

    fun teacherName(o: JsonObject) = TeacherName(id = o.s("id")!!, name = o.s("name") ?: "", active = o.b("active") ?: true)

    fun notification(o: JsonObject) = AppNotification(
        id = o.s("id")!!, userId = o.s("user_id") ?: "", title = o.s("title") ?: "", body = o.s("body") ?: "",
        kind = o.s("kind") ?: "info", refId = o.s("ref_id"), read = o.s("read_at") != null, createdAt = ts(o.s("created_at"))
    )
}
