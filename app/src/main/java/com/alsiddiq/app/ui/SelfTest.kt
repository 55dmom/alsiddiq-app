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
    suspend fun run(c: AppContainer, user: Member, tryStudent: String?) {
        delay(3000)
        val repo = c.center
        repo.refresh().onFailure { Log.i(TAG, "refresh fail ${it.message}") }
        val snap = repo.snapshot.value
        Log.i(TAG, "as=${user.role} name=${user.fullName} sees circles=${snap.circles.size} students=${snap.students.size} recs=${snap.recitations.size} att=${snap.attendance.size} teachers=${snap.teachers().size}")
        val s = snap.students.firstOrNull { Permissions.canRecordFor(user, it, snap) }
        if (s != null) {
            val r = repo.logRecitation(Recitation("", s.id, s.circleId, user.id, RecordKind.HIFZ, 2, 1, 2, 5, Grade.EXCELLENT, 0, "selftest-app-" + user.role.name.lowercase()))
            Log.i(TAG, "app logRecitation(${s.fullName}) " + (if (r.isSuccess) "ok" else "fail " + r.exceptionOrNull()?.message))
            val a = repo.saveAttendance(s.circleId ?: "", LocalDate.now().toString(), mapOf(s.id to AttendanceStatus.ABSENT))
            Log.i(TAG, "app saveAttendance(${s.fullName}) " + (if (a.isSuccess) "ok" else "fail " + a.exceptionOrNull()?.message))
        } else Log.i(TAG, "app write: no student this role may record for (expected for the viewer)")
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
