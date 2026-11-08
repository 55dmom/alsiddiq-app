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
    /** v3.3: the setup asks for the admin and the general passwords only (each teacher gets his own from the admin). */
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
    /** v3.1: teacher NAMES only (no passwords). Create (id = null) or rename. */
    suspend fun saveTeacherName(id: String?, name: String): Result<TeacherName>
    /** Removes the name from the list; past records keep the name they were saved with. */
    suspend fun removeTeacherName(id: String): Result<Unit>
    /** Change the general (VIEWER) or the ADMIN password. (v3.3: teachers' passwords are per teacher, [saveTeacher].) */
    suspend fun setAccessPassword(target: Role, password: String): Result<Unit>

    /** v3.3: add (id = null) or edit a teacher: name, his circle (null = none), and a new password (null = keep).
     *  Saving a stopped teacher with a new password turns him back on. */
    suspend fun saveTeacher(id: String?, name: String, circleId: String?, password: String?): Result<Member>
    /** v3.3: stop a teacher: he can't sign in any more and his circle is left without a teacher. His past records stay. */
    suspend fun removeTeacher(id: String): Result<Unit>

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
    /** v3.6 «فصل الطالب»: soft delete (admin anywhere, a teacher in his own circle). The server logs it for the admin. */
    suspend fun dismissStudent(id: String): Result<Unit>
    /** «المتفوقون» → «اظهر تفوق الطالب»: the admin publishes a congratulation card (online). */
    suspend fun publishHonorCard(students: List<HonorStudent>, message: String): Result<Unit>
    /** v3.6: mark one admin event read (null = all). */
    suspend fun markEventsRead(id: String?): Result<Unit>

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

    // v3.2 offline sync: rows still waiting («بانتظار الإرسال»), the waiting/refused counts, and the password-again step.
    val pendingKeys: StateFlow<Set<String>> get() = NO_KEYS
    val outboxInfo: StateFlow<OutboxInfo> get() = NO_OUTBOX
    suspend fun reauthenticate(owner: String, password: String): Result<Int> = Result.failure(UnsupportedOperationException())
    suspend fun dismissRejected(id: String?) {}

    // v3.4 «عن المركز» (public read; the admin edits; bundled text/photo until he does)
    val about: StateFlow<AboutPage>
    suspend fun refreshAbout()
    suspend fun aboutPhoto(path: String): ByteArray?
    suspend fun saveAboutText(body: String): Result<Unit>
    suspend fun addAboutPhoto(jpeg: ByteArray): Result<Unit>
    suspend fun removeAboutPhoto(path: String): Result<Unit>
    suspend fun saveAboutButton(b: AboutButton): Result<Unit>
    suspend fun deleteAboutButton(id: String): Result<Unit>
    suspend fun moveAboutButton(id: String, up: Boolean): Result<Unit>
}

data class ReauthTarget(val id: String, val name: String, val role: Role, val waiting: Int)
data class RejectedChange(val id: String, val label: String, val reason: String)
data class OutboxInfo(val pending: Int = 0, val needsPasswordFor: List<ReauthTarget> = emptyList(), val rejected: List<RejectedChange> = emptyList(), val lastSyncAt: Long = 0)
private val NO_KEYS: StateFlow<Set<String>> = kotlinx.coroutines.flow.MutableStateFlow(emptySet())
private val NO_OUTBOX: StateFlow<OutboxInfo> = kotlinx.coroutines.flow.MutableStateFlow(OutboxInfo())

class PermissionDenied(msg: String = "ليست لديك صلاحية لهذه العملية") : Exception(msg)
class ValidationError(msg: String) : Exception(msg)
