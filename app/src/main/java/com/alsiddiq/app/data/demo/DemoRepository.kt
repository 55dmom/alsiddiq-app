package com.alsiddiq.app.data.demo

import android.content.Context
import com.alsiddiq.app.core.Quran
import com.alsiddiq.app.data.*
import com.alsiddiq.app.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class DemoStore(
    val data: CenterSnapshot,
    val passwordHashes: Map<String, String>,
    val currentUserId: String? = null,
    val baseUserId: String? = null,
    val seedVersion: Int = DemoSeed.VERSION
)

/**
 * Local-only store used until the central database is connected. It persists to a
 * file on the device and applies the same permission rules the server will enforce,
 * so screens behave exactly as they will with the real backend. It does NOT sync.
 */
class DemoRepository(private val context: Context) : AuthRepository, CenterRepository {
    private val file = File(context.filesDir, "demo_store.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private var store: DemoStore = load()

    override val mode = BackendMode.DEMO
    private val _user = MutableStateFlow(store.data.member(store.currentUserId))
    override val currentUser: StateFlow<Member?> = _user.asStateFlow()
    private val _base = MutableStateFlow(store.data.member(store.baseUserId))
    override val baseUser: StateFlow<Member?> = _base.asStateFlow()
    override val centerName: StateFlow<String> = MutableStateFlow(store.data.center.name)
    private val _snapshot = MutableStateFlow(visibleTo(_user.value))
    override val snapshot: StateFlow<CenterSnapshot> = _snapshot.asStateFlow()
    private val _sync = MutableStateFlow<SyncState>(SyncState.LocalOnly)
    override val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    private fun load(): DemoStore = try {
        if (file.exists()) json.decodeFromString(DemoStore.serializer(), file.readText())
            .takeIf { it.seedVersion == DemoSeed.VERSION } ?: DemoSeed.create()
        else DemoSeed.create()
    } catch (e: Exception) {
        DemoSeed.create()
    }

    private fun persist() {
        val text = json.encodeToString(DemoStore.serializer(), store)
        io.launch { runCatching { file.writeText(text) } }
    }

    private fun visibleTo(user: Member?): CenterSnapshot {
        val all = store.data
        if (user == null) return CenterSnapshot(center = all.center)
        if (user.role == Role.ADMIN || user.role == Role.VIEWER) return all.copy(notifications = all.notifications.filter { it.userId == user.id })
        val students = Permissions.visibleStudents(user, all)
        val ids = students.map { it.id }.toSet()
        val circles = Permissions.visibleCircles(user, all)
        val memberIds = buildSet {
            add(user.id)
            circles.mapNotNull { it.teacherId }.forEach { add(it) }
        }
        return CenterSnapshot(
            center = all.center,
            members = all.members.filter { it.id in memberIds },
            circles = circles,
            students = students,
            recitations = all.recitations.filter { it.studentId in ids },
            attendance = all.attendance.filter { it.studentId in ids },
            rewards = all.rewards.filter { it.studentId in ids },
            announcements = all.announcements.filter { Permissions.audienceIncludes(it.audience, user.role) },
            notifications = all.notifications.filter { it.userId == user.id },
            teacherNames = all.teacherNames
        )
    }

    private suspend fun <T> mutate(block: (CenterSnapshot, Member) -> Pair<CenterSnapshot, T>): Result<T> = lock.withLock {
        val user = _user.value ?: return Result.failure(PermissionDenied("سجّل الدخول أولاً"))
        try {
            val (next, value) = block(store.data, user)
            store = store.copy(data = next)
            persist()
            _snapshot.value = visibleTo(user)
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun require(ok: Boolean) { if (!ok) throw PermissionDenied() }
    private fun newId() = UUID.randomUUID().toString()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normPw(p: String) = p.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").trim()

    // ---------------- Auth (one password field; the password decides who you are) ----------------
    override suspend fun signIn(password: String, kind: Role?): Result<Member> = lock.withLock {
        val h = hash(normPw(password))
        val m = store.data.members.firstOrNull { it.active && store.passwordHashes[it.id] == h && (kind == null || it.role == kind) }
            ?: return Result.failure(ValidationError("كلمة السر غير صحيحة"))
        val cur = _user.value
        val base = if (kind != null && cur?.role == Role.VIEWER && m.role != Role.VIEWER) cur else if (kind != null) _base.value else null
        store = store.copy(currentUserId = m.id, baseUserId = base?.id)
        persist()
        _user.value = m
        _base.value = base
        _snapshot.value = visibleTo(m)
        Result.success(m)
    }

    override suspend fun leaveRole() {
        lock.withLock {
            val base = _base.value
            store = store.copy(currentUserId = base?.id, baseUserId = null)
            persist()
            _user.value = base
            _base.value = null
            _snapshot.value = visibleTo(base)
        }
    }

    override suspend fun signOut() {
        lock.withLock {
            store = store.copy(currentUserId = null, baseUserId = null)
            persist()
            _user.value = null
            _base.value = null
            _snapshot.value = visibleTo(null)
        }
    }

    override suspend fun refresh(): Result<Unit> {
        _snapshot.value = visibleTo(_user.value)
        return Result.success(Unit)
    }

    // ---------------- Admin ----------------
    override suspend fun updateCenter(info: CenterInfo) = mutate { d, u ->
        require(Permissions.canManageCenter(u)); d.copy(center = info) to Unit
    }

    override suspend fun saveTeacherName(id: String?, name: String) = mutate { d, u ->
        require(Permissions.canManageMembers(u))
        if (name.isBlank()) throw ValidationError("اكتب اسم المعلم")
        val saved = TeacherName(id ?: newId(), name.trim())
        d.copy(teacherNames = if (d.teacherNames.any { it.id == saved.id }) d.teacherNames.map { if (it.id == saved.id) saved else it } else d.teacherNames + saved) to saved
    }

    override suspend fun removeTeacherName(id: String) = mutate { d, u ->
        require(Permissions.canManageMembers(u))
        d.copy(teacherNames = d.teacherNames.filterNot { it.id == id }) to Unit
    }

    private val prefs = context.getSharedPreferences("alsiddiq_demo_prefs", Context.MODE_PRIVATE)
    private val _recorder = MutableStateFlow(prefs.getString("recorder_name", null))
    override val recorderName: StateFlow<String?> = _recorder.asStateFlow()
    override fun setRecorderName(name: String?) {
        val n = name?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString("recorder_name", n).apply(); _recorder.value = n
    }
    private fun byName(u: Member): String? = if (u.role == Role.TEACHER) _recorder.value else null

    private val photoDir = File(context.filesDir, "demo_photos").apply { mkdirs() }
    override suspend fun setStudentPhoto(studentId: String, jpeg: ByteArray?) = mutate { d, u ->
        val s = d.student(studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canEditStudent(u, s, d))
        s.photoPath?.let { runCatching { File(photoDir, it.replace('/', '_')).delete() } }
        val path = jpeg?.let { "demo/$studentId/${newId().take(8)}.jpg" }
        if (jpeg != null && path != null) File(photoDir, path.replace('/', '_')).writeBytes(jpeg)
        d.copy(students = d.students.map { if (it.id == studentId) it.copy(photoPath = path) else it }) to Unit
    }
    override suspend fun studentPhoto(path: String): ByteArray? = runCatching { File(photoDir, path.replace('/', '_')).readBytes() }.getOrNull()

    override suspend fun setAccessPassword(target: Role, password: String): Result<Unit> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (!Permissions.canManageMembers(u)) return Result.failure(PermissionDenied())
        val pw = normPw(password)
        if (pw.length < 6) return Result.failure(ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل"))
        val acc = store.data.members.firstOrNull { it.role == target && it.active } ?: return Result.failure(ValidationError("الحساب غير موجود"))
        if (store.passwordHashes.any { (mid, ph) -> mid != acc.id && ph == hash(pw) }) return Result.failure(ValidationError("كلمة السر هذي مستخدمة لحساب ثاني، اختر غيرها"))
        store = store.copy(passwordHashes = store.passwordHashes + (acc.id to hash(pw)))
        persist()
        Result.success(Unit)
    }

    override suspend fun saveCircle(c: Circle) = mutate { d, u ->
        require(Permissions.canManageCircles(u))
        if (c.name.isBlank()) throw ValidationError("اكتب اسم الحلقة")
        val saved = if (c.id.isBlank()) c.copy(id = newId()) else c
        val list = if (d.circles.any { it.id == saved.id }) d.circles.map { if (it.id == saved.id) saved else it } else d.circles + saved
        d.copy(circles = list) to saved
    }

    override suspend fun deleteCircle(id: String) = mutate { d, u ->
        require(Permissions.canManageCircles(u))
        d.copy(
            circles = d.circles.filterNot { it.id == id },
            students = d.students.map { if (it.circleId == id) it.copy(circleId = null) else it }
        ) to Unit
    }

    override suspend fun saveStudent(s: Student) = mutate { d, u ->
        val existing = d.student(s.id)
        require(if (existing == null) Permissions.canAddStudentTo(u, s.circleId, d) else Permissions.canEditStudent(u, existing, d) && Permissions.canAddStudentTo(u, s.circleId, d))
        if (s.fullName.isBlank()) throw ValidationError("اكتب اسم الطالب")
        val saved = if (s.id.isBlank()) s.copy(id = newId()) else s
        val list = if (d.students.any { it.id == saved.id }) d.students.map { if (it.id == saved.id) saved else it } else d.students + saved
        d.copy(students = list) to saved
    }

    override suspend fun deleteStudent(id: String) = mutate { d, u ->
        require(Permissions.canManageStudents(u))
        d.copy(
            students = d.students.filterNot { it.id == id },
            recitations = d.recitations.filterNot { it.studentId == id },
            attendance = d.attendance.filterNot { it.studentId == id },
            rewards = d.rewards.filterNot { it.studentId == id }
        ) to Unit
    }

    // ---------------- Teaching ----------------
    override suspend fun logRecitation(r: Recitation) = mutate { d, u ->
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (!Quran.isValid(r.fromSurah, r.fromAyah) || !Quran.isValid(r.toSurah, r.toAyah)) throw ValidationError("رقم الآية غير صحيح")
        if (Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah) <= 0) throw ValidationError("نهاية المقطع قبل بدايته")
        val saved = r.copy(id = r.id.ifBlank { newId() }, teacherId = u.id, circleId = student.circleId, byName = r.byName ?: byName(u))
        val ayat = Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah)
        val title = "${saved.kind.label}: ${student.fullName}"
        val body = "${com.alsiddiq.app.domain.Stats.describeRange(saved)} • $ayat آية • ${saved.grade.label}"
        val notes = notify(d, recipientsFor(d, student.id), title, body, "record", saved.id)
        d.copy(recitations = d.recitations + saved, notifications = d.notifications + notes) to saved
    }

    override suspend fun deleteRecitation(id: String) = mutate { d, u ->
        val r = d.recitations.firstOrNull { it.id == id } ?: throw ValidationError("السجل غير موجود")
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        d.copy(recitations = d.recitations.filterNot { it.id == id }) to Unit
    }

    override suspend fun saveAttendance(circleId: String, day: String, marks: Map<String, AttendanceStatus>) = mutate { d, u ->
        val circle = d.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        require(u.role == Role.ADMIN || Permissions.teachesCircle(u, circle))
        val kept = d.attendance.filterNot { it.day == day && it.studentId in marks.keys }
        val added = marks.map { (sid, st) -> Attendance(newId(), sid, circleId, day, st, u.id, byName = byName(u)) }
        val notes = marks.filter { it.value == AttendanceStatus.ABSENT }.flatMap { (sid, _) ->
            val s = d.student(sid)
            notify(d, recipientsFor(d, sid), "غياب ${s?.fullName ?: ""}", "سُجّل غياب في ${circle.name} بتاريخ $day", "absence", sid)
        }
        d.copy(attendance = kept + added, notifications = d.notifications + notes) to added.size
    }

    override suspend fun giveReward(r: Reward) = mutate { d, u ->
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (r.points == 0) throw ValidationError("حدّد عدد النقاط")
        val saved = r.copy(id = r.id.ifBlank { newId() }, awardedBy = u.id, byName = r.byName ?: byName(u))
        val notes = notify(d, recipientsFor(d, student.id), "مكافأة ${student.fullName} 🎉", "+${r.points} نقطة: ${r.reason}", "reward", saved.id)
        d.copy(rewards = d.rewards + saved, notifications = d.notifications + notes) to saved
    }

    // ---------------- Communication ----------------
    override suspend fun postAnnouncement(a: Announcement) = mutate { d, u ->
        require(Permissions.canPostAnnouncement(u))
        if (a.title.isBlank()) throw ValidationError("اكتب عنوان الإعلان")
        val saved = a.copy(id = a.id.ifBlank { newId() }, createdBy = u.id, byName = a.byName ?: byName(u))
        val targets = d.members.filter { it.id != u.id && Permissions.audienceIncludes(a.audience, it.role) }.map { it.id }
        val notes = notify(d, targets, "إعلان: ${a.title}", a.body.take(120), "announcement", saved.id)
        d.copy(announcements = d.announcements + saved, notifications = d.notifications + notes) to saved
    }

    override suspend fun deleteAnnouncement(id: String) = mutate { d, u ->
        val a = d.announcements.firstOrNull { it.id == id } ?: throw ValidationError("الإعلان غير موجود")
        require(u.role == Role.ADMIN || a.createdBy == u.id)
        d.copy(announcements = d.announcements.filterNot { it.id == id }) to Unit
    }

    override suspend fun markNotificationRead(id: String) = mutate { d, u ->
        d.copy(notifications = d.notifications.map { if (it.id == id && it.userId == u.id) it.copy(read = true) else it }) to Unit
    }

    override suspend fun markAllNotificationsRead() = mutate { d, u ->
        d.copy(notifications = d.notifications.map { if (it.userId == u.id) it.copy(read = true) else it }) to Unit
    }

    private fun recipientsFor(d: CenterSnapshot, studentId: String): List<String> =
        d.members.filter { it.role == Role.ADMIN }.map { it.id }

    private fun notify(d: CenterSnapshot, userIds: List<String>, title: String, body: String, kind: String, ref: String?) =
        userIds.distinct().map { AppNotification(newId(), it, title, body, kind, ref) }
}
