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

/**
 * v3 access: there are no usernames or phone numbers. One password field; the server decides whose
 * password it is (the general viewer password, the admin password, or one teacher's password).
 */
interface AuthRepository {
    val mode: BackendMode
    /** Who is using the app right now (viewer, a teacher, or the admin). */
    val currentUser: StateFlow<Member?>
    /** The general (viewer) session kept underneath while a teacher/admin is signed in from Settings. */
    val baseUser: StateFlow<Member?>

    /**
     * kind = null: the opening screen (any password). kind = ADMIN / TEACHER: the Settings buttons
     * «الإدارة» / «المعلمين»; the general view stays underneath and comes back on [leaveRole].
     */
    suspend fun signIn(password: String, kind: Role? = null): Result<Member>
    /** Leave the admin/teacher section and return to the general view (or the password screen if none). */
    suspend fun leaveRole()
    /** Forget every session on this phone. */
    suspend fun signOut()

    /** True when no admin password exists yet (first-time setup). Null = unknown (offline). */
    suspend fun needsSetup(): Boolean? = false
    /** The centre name shown on the password screen (cached). */
    val centerName: StateFlow<String>
    suspend fun setup(centerName: String, adminPassword: String, viewerPassword: String): Result<Member> =
        Result.failure(UnsupportedOperationException())
}

interface CenterRepository {
    /** Everything the current user is allowed to see; updates live. */
    val snapshot: StateFlow<CenterSnapshot>
    val syncState: StateFlow<SyncState>

    suspend fun refresh(): Result<Unit>

    // Admin: centre, teachers, passwords, circles, students
    suspend fun updateCenter(info: CenterInfo): Result<Unit>
    /** Create (id = null) or update a teacher: name, their circle, and (optionally) a new password. */
    suspend fun saveTeacher(id: String?, name: String, circleId: String?, password: String?): Result<Member>
    /** The teacher can no longer sign in; their past records keep their name. */
    suspend fun removeTeacher(id: String): Result<Unit>
    /** Change the general (VIEWER) or the ADMIN password. */
    suspend fun setAccessPassword(target: Role, password: String): Result<Unit>
    suspend fun saveCircle(c: Circle): Result<Circle>
    suspend fun deleteCircle(id: String): Result<Unit>
    suspend fun saveStudent(s: Student): Result<Student>
    suspend fun deleteStudent(id: String): Result<Unit>

    // Teaching
    suspend fun logRecitation(r: Recitation): Result<Recitation>
    suspend fun deleteRecitation(id: String): Result<Unit>
    suspend fun saveAttendance(circleId: String, day: String, marks: Map<String, AttendanceStatus>): Result<Int>
    suspend fun giveReward(r: Reward): Result<Reward>

    // Communication (teachers' circle reports are announcements to everyone)
    suspend fun postAnnouncement(a: Announcement): Result<Announcement>
    suspend fun deleteAnnouncement(id: String): Result<Unit>
    suspend fun markNotificationRead(id: String): Result<Unit>
    suspend fun markAllNotificationsRead(): Result<Unit>
}

class PermissionDenied(msg: String = "ليست لديك صلاحية لهذه العملية") : Exception(msg)
class ValidationError(msg: String) : Exception(msg)
