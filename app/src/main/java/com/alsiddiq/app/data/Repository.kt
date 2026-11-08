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
    /** v3.1: the setup also creates the ONE shared teachers' password. */
    suspend fun setup(centerName: String, adminPassword: String, viewerPassword: String, teachersPassword: String): Result<Member> =
        Result.failure(UnsupportedOperationException())
}

interface CenterRepository {
    /** Everything the current user is allowed to see; updates live. */
    val snapshot: StateFlow<CenterSnapshot>
    val syncState: StateFlow<SyncState>

    suspend fun refresh(): Result<Unit>

    // Admin: centre, teachers, passwords, circles, students
    suspend fun updateCenter(info: CenterInfo): Result<Unit>
    /** v3.1: teacher NAMES only (no passwords). Create (id = null) or rename. */
    suspend fun saveTeacherName(id: String?, name: String): Result<TeacherName>
    /** Removes the name from the list; past records keep the name they were saved with. */
    suspend fun removeTeacherName(id: String): Result<Unit>
    /** Change the general (VIEWER), the ADMIN, or the shared teachers' (TEACHER) password. */
    suspend fun setAccessPassword(target: Role, password: String): Result<Unit>

    /** v3.1: the teacher's own name picked on this phone (optional); every record then carries it. */
    val recorderName: StateFlow<String?>
    fun setRecorderName(name: String?)

    /** v3.1: set (jpeg != null) or remove (null) a student's photo. */
    suspend fun setStudentPhoto(studentId: String, jpeg: ByteArray?): Result<Unit>
    /** The photo's bytes (cached on the phone), or null. */
    suspend fun studentPhoto(path: String): ByteArray?
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
