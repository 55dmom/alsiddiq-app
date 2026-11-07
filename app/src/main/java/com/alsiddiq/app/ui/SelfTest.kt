package com.alsiddiq.app.ui

import android.util.Log
import com.alsiddiq.app.AppContainer
import com.alsiddiq.app.domain.*
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Debug-only: exercises the real write → server → read path and logs the outcome for CI. */
object SelfTest {
    private const val TAG = "ALSIDDIQ_SELFTEST"
    suspend fun run(c: AppContainer, user: Member) {
        delay(3000)
        val repo = c.center
        repo.refresh().onFailure { Log.i(TAG, "refresh fail ${it.message}") }
        val snap = repo.snapshot.value
        Log.i(TAG, "snapshot circles=${snap.circles.size} students=${snap.students.size} recs=${snap.recitations.size} members=${snap.members.size}")
        val s = snap.students.firstOrNull { Permissions.canRecordFor(user, it, snap) }
        if (s == null) { Log.i(TAG, "no student to record for"); return }
        val r = repo.logRecitation(Recitation("", s.id, s.circleId, user.id, RecordKind.HIFZ, 2, 1, 2, 5, Grade.EXCELLENT, 1, "selftest"))
        Log.i(TAG, "logRecitation " + (if (r.isSuccess) "ok id=" + r.getOrNull()?.id else "fail " + r.exceptionOrNull()?.message))
        val a = repo.saveAttendance(s.circleId ?: "", LocalDate.now().toString(), mapOf(s.id to AttendanceStatus.ABSENT))
        Log.i(TAG, "saveAttendance " + (if (a.isSuccess) "ok" else "fail " + a.exceptionOrNull()?.message))
        val other = snap.students.firstOrNull { !Permissions.canRecordFor(user, it, snap) }
        delay(2000)
        repo.refresh()
        val after = repo.snapshot.value
        Log.i(TAG, "after recs=${after.recitations.size} att=${after.attendance.size} sync=${repo.syncState.value} other_visible=${other != null}")
        Log.i(TAG, "done")
    }
}
