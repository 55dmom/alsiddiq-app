package com.alsiddiq.app.data

import com.alsiddiq.app.domain.*
import kotlinx.coroutines.flow.StateFlow

/** Where the data lives. The UI only ever talks to these interfaces. */
enum class BackendMode { DEMO, CLOUD }

sealed interface SyncState {
    data object LocalOnly : SyncState
    data object Synced : SyncState
    data class Pending(val count: Int) : SyncState
    data class Error(val message: String) : SyncState
}

interface AuthRepository {
    val mode: BackendMode
    val currentUser: StateFlow<Member?>
    suspend fun signIn(login: String, password: String): Result<Member>
    suspend fun signOut()
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    /** True when the central database has no accounts yet (first-time setup). Null = unknown (offline). */
    suspend fun needsSetup(): Boolean? = false
    suspend fun bootstrap(centerName: String, fullName: String, phone: String, password: String): Result<Member> =
        Result.failure(UnsupportedOperationException())
}

data class NewMember(
    val fullName: String,
    val phone: String,
    val role: Role,
    val password: String,
    val studentId: String? = null,
    val childIds: List<String> = emptyList()
)

interface CenterRepository {
    /** Everything the current user is allowed to see; updates live. */
    val snapshot: StateFlow<CenterSnapshot>
    val syncState: StateFlow<SyncState>

    suspend fun refresh(): Result<Unit>

    // Admin: center, members, circles, students
    suspend fun updateCenter(info: CenterInfo): Result<Unit>
    suspend fun createMember(m: NewMember): Result<Member>
    suspend fun updateMember(m: Member): Result<Unit>
    suspend fun resetMemberPassword(memberId: String, newPassword: String): Result<Unit>
    suspend fun saveCircle(c: Circle): Result<Circle>
    suspend fun deleteCircle(id: String): Result<Unit>
    suspend fun saveStudent(s: Student): Result<Student>
    suspend fun deleteStudent(id: String): Result<Unit>

    // Teaching
    suspend fun logRecitation(r: Recitation): Result<Recitation>
    suspend fun deleteRecitation(id: String): Result<Unit>
    suspend fun saveAttendance(circleId: String, day: String, marks: Map<String, AttendanceStatus>): Result<Int>
    suspend fun giveReward(r: Reward): Result<Reward>

    // Communication
    suspend fun postAnnouncement(a: Announcement): Result<Announcement>
    suspend fun deleteAnnouncement(id: String): Result<Unit>
    suspend fun markNotificationRead(id: String): Result<Unit>
    suspend fun markAllNotificationsRead(): Result<Unit>
}

class PermissionDenied(msg: String = "ليست لديك صلاحية لهذه العملية") : Exception(msg)
class ValidationError(msg: String) : Exception(msg)
