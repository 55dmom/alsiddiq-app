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
    val seedVersion: Int = DemoSeed.VERSION
)

/**
 * Local-only store used until the central database is connected. It persists to a
 * file on the device and applies the same permission rules the server will enforce,
 * so screens behave exactly as they will with the real backend. It does NOT sync.
 */
class DemoRepository(context: Context) : AuthRepository, CenterRepository {
    private val file = File(context.filesDir, "demo_store.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private var store: DemoStore = load()

    override val mode = BackendMode.DEMO
    private val _user = MutableStateFlow(store.data.member(store.currentUserId))
    override val currentUser: StateFlow<Member?> = _user.asStateFlow()
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
        if (user.role == Role.ADMIN) return all.copy(notifications = all.notifications.filter { it.userId == user.id })
        val students = Permissions.visibleStudents(user, all)
        val ids = students.map { it.id }.toSet()
        val circles = Permissions.visibleCircles(user, all)
        val memberIds = buildSet {
            add(user.id)
            circles.mapNotNull { it.teacherId }.forEach { add(it) }
            if (user.role == Role.TEACHER) all.members.filter { m -> m.role == Role.PARENT && m.childIds.any { it in ids } }.forEach { add(it.id) }
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
            notifications = all.notifications.filter { it.userId == user.id }
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

    private fun normalisePhone(p: String) = p.filter { it.isDigit() }
        .map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")

    // ---------------- Auth ----------------
    override suspend fun signIn(login: String, password: String): Result<Member> = lock.withLock {
        val key = normalisePhone(login)
        val m = store.data.members.firstOrNull { normalisePhone(it.phone) == key && key.isNotEmpty() }
            ?: return Result.failure(ValidationError("لا يوجد حساب بهذا الرقم"))
        if (!m.active) return Result.failure(ValidationError("هذا الحساب موقوف، راجع إدارة المركز"))
        if (store.passwordHashes[m.id] != hash(password)) return Result.failure(ValidationError("كلمة المرور غير صحيحة"))
        store = store.copy(currentUserId = m.id)
        persist()
        _user.value = m
        _snapshot.value = visibleTo(m)
        Result.success(m)
    }

    override suspend fun signOut() {
        lock.withLock {
            store = store.copy(currentUserId = null)
            persist()
            _user.value = null
            _snapshot.value = visibleTo(null)
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (store.passwordHashes[u.id] != hash(oldPassword)) return Result.failure(ValidationError("كلمة المرور الحالية غير صحيحة"))
        if (newPassword.length < 6) return Result.failure(ValidationError("كلمة المرور 6 أحرف على الأقل"))
        store = store.copy(passwordHashes = store.passwordHashes + (u.id to hash(newPassword)))
        persist()
        Result.success(Unit)
    }

    override suspend fun refresh(): Result<Unit> {
        _snapshot.value = visibleTo(_user.value)
        return Result.success(Unit)
    }

    // ---------------- Admin ----------------
    override suspend fun updateCenter(info: CenterInfo) = mutate { d, u ->
        require(Permissions.canManageCenter(u)); d.copy(center = info) to Unit
    }

    override suspend fun createMember(m: NewMember): Result<Member> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (!Permissions.canManageMembers(u)) return Result.failure(PermissionDenied())
        val phone = normalisePhone(m.phone)
        if (m.fullName.isBlank()) return Result.failure(ValidationError("اكتب الاسم"))
        if (phone.length < 8) return Result.failure(ValidationError("رقم الجوال غير صحيح"))
        if (store.data.members.any { normalisePhone(it.phone) == phone }) return Result.failure(ValidationError("هذا الرقم مسجّل مسبقاً"))
        if (m.password.length < 6) return Result.failure(ValidationError("كلمة المرور 6 أحرف على الأقل"))
        val member = Member(newId(), m.fullName.trim(), phone, m.role, true, m.studentId, m.childIds)
        store = store.copy(
            data = store.data.copy(members = store.data.members + member),
            passwordHashes = store.passwordHashes + (member.id to hash(m.password))
        )
        persist()
        _snapshot.value = visibleTo(u)
        Result.success(member)
    }

    override suspend fun updateMember(m: Member) = mutate { d, u ->
        require(Permissions.canManageMembers(u))
        if (m.id == u.id && m.role != u.role) throw ValidationError("لا يمكنك تغيير دورك بنفسك")
        d.copy(members = d.members.map { if (it.id == m.id) m else it }) to Unit
    }

    override suspend fun resetMemberPassword(memberId: String, newPassword: String): Result<Unit> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (!Permissions.canManageMembers(u)) return Result.failure(PermissionDenied())
        if (newPassword.length < 6) return Result.failure(ValidationError("كلمة المرور 6 أحرف على الأقل"))
        store = store.copy(passwordHashes = store.passwordHashes + (memberId to hash(newPassword)))
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
        require(Permissions.canManageStudents(u))
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
            rewards = d.rewards.filterNot { it.studentId == id },
            members = d.members.map { m -> m.copy(childIds = m.childIds - id) }
        ) to Unit
    }

    // ---------------- Teaching ----------------
    override suspend fun logRecitation(r: Recitation) = mutate { d, u ->
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (!Quran.isValid(r.fromSurah, r.fromAyah) || !Quran.isValid(r.toSurah, r.toAyah)) throw ValidationError("رقم الآية غير صحيح")
        if (Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah) <= 0) throw ValidationError("نهاية المقطع قبل بدايته")
        val saved = r.copy(id = r.id.ifBlank { newId() }, teacherId = u.id, circleId = student.circleId)
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
        val added = marks.map { (sid, st) -> Attendance(newId(), sid, circleId, day, st, u.id) }
        val notes = marks.filter { it.value == AttendanceStatus.ABSENT }.flatMap { (sid, _) ->
            val s = d.student(sid)
            notify(d, d.parentsOf(sid).map { it.id }, "غياب ${s?.fullName ?: ""}", "سُجّل غياب في ${circle.name} بتاريخ $day", "absence", sid)
        }
        d.copy(attendance = kept + added, notifications = d.notifications + notes) to added.size
    }

    override suspend fun giveReward(r: Reward) = mutate { d, u ->
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (r.points == 0) throw ValidationError("حدّد عدد النقاط")
        val saved = r.copy(id = r.id.ifBlank { newId() }, awardedBy = u.id)
        val notes = notify(d, recipientsFor(d, student.id), "مكافأة ${student.fullName} 🎉", "+${r.points} نقطة: ${r.reason}", "reward", saved.id)
        d.copy(rewards = d.rewards + saved, notifications = d.notifications + notes) to saved
    }

    // ---------------- Communication ----------------
    override suspend fun postAnnouncement(a: Announcement) = mutate { d, u ->
        require(Permissions.canPostAnnouncement(u))
        if (a.title.isBlank()) throw ValidationError("اكتب عنوان الإعلان")
        val saved = a.copy(id = a.id.ifBlank { newId() }, createdBy = u.id)
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
        d.parentsOf(studentId).map { it.id } + d.members.filter { it.role == Role.STUDENT && it.studentId == studentId }.map { it.id }

    private fun notify(d: CenterSnapshot, userIds: List<String>, title: String, body: String, kind: String, ref: String?) =
        userIds.distinct().map { AppNotification(newId(), it, title, body, kind, ref) }
}
