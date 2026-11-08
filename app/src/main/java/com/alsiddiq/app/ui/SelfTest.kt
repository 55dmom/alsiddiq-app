package com.alsiddiq.app.ui

import android.util.Log
import com.alsiddiq.app.AppContainer
import com.alsiddiq.app.domain.*
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Debug-only (CI against the real database): logs what this password may see, then
 *  1) writes through the app's normal path for a student it is allowed to record for, and
 *  2) probes RAW writes (bypassing the app's own checks) on the student named by try_student,
 *     so the log shows the SERVER's own answer (201 = allowed, 401/403 = refused by the server rules).
 */
object SelfTest {
    private const val TAG = "ALSIDDIQ_SELFTEST"
    suspend fun run(c: AppContainer, user: Member, tryStudent: String?, photoTest: Boolean = false) {
        delay(3000)
        val repo = c.center
        repo.refresh().onFailure { Log.i(TAG, "refresh fail ${it.message}") }
        val snap = repo.snapshot.value
        Log.i(TAG, "as=${user.role} name=${user.fullName} sees circles=${snap.circles.size} students=${snap.students.size} recs=${snap.recitations.size} att=${snap.attendance.size} teachers=${snap.teachers().size}")
        // v3.1: the shared teachers' password must reach EVERY circle: write for the first student of each circle.
        val targets = snap.students.filter { Permissions.canRecordFor(user, it, snap) }.groupBy { it.circleId }.values.map { it.first() }
        if (user.role == Role.TEACHER) c.center.setRecorderName(snap.activeTeacherNames().firstOrNull()?.name)
        for (s in targets) {
            val r = repo.logRecitation(Recitation("", s.id, s.circleId, user.id, RecordKind.HIFZ, 2, 1, 2, 5, Grade.EXCELLENT, 0, "selftest-app-" + user.role.name.lowercase()))
            Log.i(TAG, "app logRecitation(${s.fullName}) " + (if (r.isSuccess) "ok" else "fail " + r.exceptionOrNull()?.message))
            val a = repo.saveAttendance(s.circleId ?: "", LocalDate.now().toString(), mapOf(s.id to AttendanceStatus.ABSENT))
            Log.i(TAG, "app saveAttendance(${s.fullName}) " + (if (a.isSuccess) "ok" else "fail " + a.exceptionOrNull()?.message))
            val e = repo.saveStudent(s.copy(notes = "selftest-edit-app-" + user.role.name.lowercase()))
            Log.i(TAG, "app editStudent(${s.fullName}) " + (if (e.isSuccess) "ok" else "fail " + e.exceptionOrNull()?.message))
        }
        if (targets.isEmpty()) Log.i(TAG, "app write: no student this role may record for (expected for the viewer)")
        // v3.1 photo: through the app's own path (shrink to 512 px JPEG, upload, link) for the first student.
        val ps = targets.firstOrNull()
        if (ps != null && photoTest) {
            val bmp = android.graphics.Bitmap.createBitmap(900, 1200, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.drawColor(android.graphics.Color.rgb(31, 58, 104))
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(201, 162, 39) }
            cv.drawCircle(450f, 470f, 210f, p); cv.drawOval(150f, 760f, 750f, 1400f, p)
            val bytes = com.alsiddiq.app.ui.components.PhotoUtil.compress(bmp)
            val r = repo.setStudentPhoto(ps.id, bytes)
            Log.i(TAG, "app setStudentPhoto(${ps.fullName}) bytes=${bytes.size} " + (if (r.isSuccess) "ok path=${repo.snapshot.value.student(ps.id)?.photoPath}" else "fail " + r.exceptionOrNull()?.message))
        }
        if (user.role == Role.VIEWER) {
            val vs = snap.students.firstOrNull()
            if (vs != null) { val r = repo.setStudentPhoto(vs.id, ByteArray(10)); Log.i(TAG, "viewer setStudentPhoto " + (if (r.isSuccess) "ok(!)" else "refused " + r.exceptionOrNull()?.message)) }
            val withPhoto = snap.students.firstOrNull { it.photoPath != null }
            if (withPhoto != null) Log.i(TAG, "viewer reads photo of ${withPhoto.fullName}: " + (repo.studentPhoto(withPhoto.photoPath!!)?.size?.let { "$it bytes" } ?: "none"))
        }
        val cloud = c.cloud
        if (cloud != null && !tryStudent.isNullOrBlank()) {
            val (sid, cid) = tryStudent.split(":").let { it[0] to it.getOrNull(1) }
            val res = runCatching { cloud.debugRawWrites(sid, cid) }.getOrElse { listOf("error ${it.message}") }
            Log.i(TAG, "raw probe on $sid as ${user.role}: " + res.joinToString(" "))
        }
        delay(1500)
        repo.refresh()
        Log.i(TAG, "after recs=${repo.snapshot.value.recitations.size} att=${repo.snapshot.value.attendance.size} sync=${repo.syncState.value}")
        Log.i(TAG, "done")
    }
}
