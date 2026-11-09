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
        // v3.3: teachers read the whole centre too (they edit only their own circle).
        if (user.role == Role.ADMIN || user.role == Role.VIEWER || user.role == Role.TEACHER) return all.copy(
            notifications = all.notifications.filter { it.userId == user.id },
            events = if (user.role == Role.ADMIN) all.events else emptyList(),
            announcements = all.announcements.filter { Permissions.audienceIncludes(it.audience, user.role) })
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
    /** v3.3: a teacher's records carry his own name. */
    private fun byName(u: Member): String? = if (u.role == Role.TEACHER && !u.allCircles) u.fullName else null

    override suspend fun saveTeacher(id: String?, name: String, circleId: String?, password: String?): Result<Member> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (!Permissions.canManageMembers(u)) return Result.failure(PermissionDenied())
        if (name.isBlank()) return Result.failure(ValidationError("اكتب اسم المعلم"))
        val pw = password?.let { normPw(it) }?.takeIf { it.isNotEmpty() }
        val existing = store.data.member(id)?.takeIf { it.role == Role.TEACHER }
        if (existing == null && pw == null) return Result.failure(ValidationError("اختر كلمة سر للمعلم"))
        if (existing != null && !existing.active && pw == null) return Result.failure(ValidationError("المعلم موقوف: اختر له كلمة سر جديدة لتشغيله"))
        if (pw != null && pw.length < 6) return Result.failure(ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل"))
        val mid = existing?.id ?: newId()
        if (pw != null && store.passwordHashes.any { (k, ph) -> k != mid && ph == hash(pw) }) return Result.failure(ValidationError("كلمة السر هذي مستخدمة لحساب ثاني، اختر غيرها"))
        val saved = (existing ?: Member(mid, "", "", Role.TEACHER)).copy(fullName = name.trim(), active = true)
        val d0 = store.data
        val members = if (existing != null) d0.members.map { if (it.id == mid) saved else it } else d0.members + saved
        val circles = d0.circles.map { c -> when {
            c.id == circleId -> c.copy(teacherId = mid, teacherName = saved.fullName, teacherIds = (c.teacherIds - setOfNotNull(c.teacherId) + mid).distinct())
            c.teacherId == mid -> c.copy(teacherId = null, teacherName = null, teacherIds = c.teacherIds - mid)
            else -> c } }
        store = store.copy(data = d0.copy(members = members, circles = circles),
            passwordHashes = if (pw != null) store.passwordHashes + (mid to hash(pw)) else store.passwordHashes)
        persist(); _snapshot.value = visibleTo(u)
        Result.success(saved)
    }

    override suspend fun removeTeacher(id: String): Result<Unit> = lock.withLock {
        val u = _user.value ?: return Result.failure(PermissionDenied())
        if (!Permissions.canManageMembers(u)) return Result.failure(PermissionDenied())
        val d0 = store.data
        store = store.copy(data = d0.copy(
            members = d0.members.map { if (it.id == id) it.copy(active = false) else it },
            circles = d0.circles.map { if (it.teacherId == id) it.copy(teacherId = null, teacherName = null) else it }),
            passwordHashes = store.passwordHashes - id)
        persist(); _snapshot.value = visibleTo(u)
        Result.success(Unit)
    }

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
        // v3.6 (the server's trigger does this for real): a teacher's new student shows on the admin's bell.
        val ev = if (existing == null && u.role == Role.TEACHER) AdminEvent(newId(), "student_added", u.id, u.fullName, saved.id, saved.fullName,
            d.circle(saved.circleId)?.name ?: "", saved.circleId, saved.phone, System.currentTimeMillis()) else null
        d.copy(students = list, events = listOfNotNull(ev) + d.events) to saved
    }

    override suspend fun dismissStudent(id: String) = mutate { d, u ->
        val s = d.students.firstOrNull { it.id == id } ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canDismissStudent(u, s, d))
        val now = System.currentTimeMillis()
        val gone = s.copy(active = false, dismissedAt = now, dismissedBy = u.id)
        val ev = if (u.role == Role.TEACHER) AdminEvent(newId(), "student_dismissed", u.id, u.fullName, s.id, s.fullName,
            d.circle(s.circleId)?.name ?: "", s.circleId, s.phone, now) else null
        d.copy(students = d.students.filterNot { it.id == id }, dismissed = d.dismissed + gone, events = listOfNotNull(ev) + d.events) to Unit
    }

    override suspend fun publishHonorCard(students: List<HonorStudent>, message: String) = mutate { d, u ->
        require(u.role == Role.ADMIN)
        if (students.isEmpty()) throw ValidationError("اختر طالباً واحداً على الأقل")
        d.copy(honorCards = listOf(HonorCard(newId(), students, message.trim(), u.id, System.currentTimeMillis())) + d.honorCards) to Unit
    }

    override suspend fun markEventsRead(id: String?) = mutate { d, u ->
        require(u.role == Role.ADMIN)
        val now = System.currentTimeMillis()
        d.copy(events = d.events.map { if ((id == null || it.id == id) && it.readAt == null) it.copy(readAt = now) else it }) to Unit
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
        if (com.alsiddiq.app.domain.RestDay.isRest(java.time.LocalDate.now())) throw ValidationError(com.alsiddiq.app.domain.RestDay.MESSAGE)
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
        if (com.alsiddiq.app.domain.RestDay.isRest(java.time.LocalDate.parse(day))) throw ValidationError(com.alsiddiq.app.domain.RestDay.MESSAGE)
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

    // ---------------- v3.4 «عن المركز» (demo: kept on this phone only) ----------------
    private val aboutLocal = AboutLocal(context)
    override val about: StateFlow<AboutPage> = aboutLocal.flow.asStateFlow()
    override suspend fun refreshAbout() {}
    override suspend fun aboutPhoto(path: String): ByteArray? = aboutLocal.cached(path)
    private fun adminOnly() { if (_user.value?.role != Role.ADMIN) throw PermissionDenied() }
    override suspend fun saveAboutText(body: String): Result<Unit> = runCatching { adminOnly(); aboutLocal.set(aboutLocal.page.copy(body = body.trim(), updatedAt = System.currentTimeMillis())) }
    override suspend fun addAboutPhoto(jpeg: ByteArray): Result<Unit> = runCatching {
        adminOnly(); val p = "demo/" + newId().take(8) + ".jpg"; aboutLocal.cache(p, jpeg)
        aboutLocal.set(aboutLocal.page.copy(body = aboutLocal.currentText(), photos = aboutLocal.page.photos + p))
    }
    override suspend fun removeAboutPhoto(path: String): Result<Unit> = runCatching {
        adminOnly(); aboutLocal.set(aboutLocal.page.copy(body = aboutLocal.currentText(), photos = aboutLocal.page.photos - path)); aboutLocal.forget(path)
    }
    override suspend fun saveAboutButton(b: AboutButton): Result<Unit> = runCatching {
        adminOnly(); if (b.label.isBlank() || b.value.isBlank()) throw ValidationError("اكتب اسم الزر وقيمته")
        val list = aboutLocal.page.buttons
        val saved = if (b.id.isBlank()) b.copy(id = newId(), sort = (list.maxOfOrNull { it.sort } ?: -1) + 1) else b
        aboutLocal.set(aboutLocal.page.copy(buttons = AboutLocal.sorted(list.filterNot { it.id == saved.id } + saved)))
    }
    override suspend fun deleteAboutButton(id: String): Result<Unit> = runCatching { adminOnly(); aboutLocal.set(aboutLocal.page.copy(buttons = aboutLocal.page.buttons.filterNot { it.id == id })) }

    // ---------------- v3.8 (demo, on this phone only): teachers' attendance, budget, subscriptions, circle photo ----------------
    @Serializable
    private data class V38Store(val marks: List<TeacherMark> = emptyList(), val budget: List<BudgetEntry> = emptyList(), val subs: List<SubscriptionMark> = emptyList())
    private val v38File = File(context.filesDir, "demo_v38.json")
    private var v38: V38Store = runCatching { json.decodeFromString(V38Store.serializer(), v38File.readText()) }.getOrNull() ?: seedV38()
    private fun saveV38() { val t = json.encodeToString(V38Store.serializer(), v38); io.launch { runCatching { v38File.writeText(t) } } }
    private fun seedV38(): V38Store {
        val today = java.time.LocalDate.now()
        val teachers = store.data.members.filter { it.role == Role.TEACHER && it.active && !it.allCircles }
        val marks = (0..9).map { today.minusDays(it.toLong()) }.filterNot { com.alsiddiq.app.domain.RestDay.isRest(it) }.take(6).flatMap { dd -> val back = today.toEpochDay() - dd.toEpochDay(); val d = dd.toString()
            teachers.mapIndexed { i, t ->
                val st = when { i == 2 && back == 2L -> TeacherStatus.ABSENT; i == 0 && back == 4L -> TeacherStatus.ABSENT; i == 1 && back % 3 == 0L -> TeacherStatus.EXCUSED; else -> TeacherStatus.PRESENT }
                TeacherMark(t.id, d, st, if (st == TeacherStatus.ABSENT) null else "07:55", if (st == TeacherStatus.ABSENT) null else "13:05") } }
        val st = store.data.students.filter { it.active }
        val m0 = today.withDayOfMonth(1)
        val budget = st.take(4).mapIndexed { i, s -> BudgetEntry("b-in-$i", BudgetEntry.INCOME, (store.data.circle(s.circleId)?.feeYer ?: 3000).toDouble(),
                m0.plusDays(i.toLong()).toString(), s.id, if (i == 0) "اشتراك شهر" else "") } +
            listOf(BudgetEntry("b-ex-1", BudgetEntry.EXPENSE, 4500.0, m0.plusDays(2).toString(), null, "شراء مصاحف للحلقات"),
                BudgetEntry("b-ex-2", BudgetEntry.EXPENSE, 2000.0, m0.plusDays(4).toString(), null, "كهرباء المسجد"),
                BudgetEntry("b-ex-3", BudgetEntry.EXPENSE, 1500.0, m0.minusDays(10).toString(), null, "جوائز المتفوقين"))
        val subs = st.filter { it.circleId == "c1" || it.circleId == "c2" }.mapIndexed { i, s ->
            val fee = (store.data.circle(s.circleId)?.feeYer ?: 0).toDouble()
            SubscriptionMark(s.id, m0.toString(), i % 3 != 2, if (i % 3 != 2) fee else null) }
        return V38Store(marks, budget, subs)
    }
    private fun adminV38() { if (_user.value?.role != Role.ADMIN) throw PermissionDenied() }

    override suspend fun teacherMarks(fromDay: String, toDay: String): Result<List<TeacherMark>> = runCatching {
        adminV38(); v38.marks.filter { it.day >= fromDay && it.day <= toDay }.sortedByDescending { it.day }
    }
    override suspend fun saveTeacherMarks(day: String, marks: List<TeacherMark>): Result<Int> = runCatching {
        adminV38(); if (marks.isEmpty()) throw ValidationError("حدّد حاضر أو غائب أو مستأذن لمعلم واحد على الأقل")
        if (com.alsiddiq.app.domain.RestDay.isRest(java.time.LocalDate.parse(day))) throw ValidationError(com.alsiddiq.app.domain.RestDay.MESSAGE)
        val ids = marks.map { it.teacherId }.toSet()
        v38 = v38.copy(marks = v38.marks.filterNot { it.day == day && it.teacherId in ids } + marks.map { if (it.status == TeacherStatus.ABSENT) it.copy(checkIn = null, checkOut = null) else it }); saveV38(); marks.size
    }
    override suspend fun saveContactButton(on: Boolean, phone: String): Result<Unit> = runCatching { adminV38(); aboutLocal.set(aboutLocal.page.copy(contactButton = on, contactPhone = phone.trim())) }
    override suspend fun budgetEntries(): Result<List<BudgetEntry>> = runCatching { adminV38(); v38.budget.sortedWith(compareByDescending<BudgetEntry> { it.day }.thenByDescending { it.createdAt }) }
    override suspend fun saveBudgetEntry(e: BudgetEntry, isNew: Boolean): Result<Unit> = runCatching {
        adminV38()
        if (e.amount <= 0) throw ValidationError("اكتب مبلغاً أكبر من صفر")
        if (!e.isIncome && e.note.isBlank()) throw ValidationError("اكتب ملاحظة الصرف")
        v38 = v38.copy(budget = if (isNew) v38.budget + e else v38.budget.map { if (it.id == e.id) e else it }); saveV38()
    }
    override suspend fun deleteBudgetEntry(id: String): Result<Unit> = runCatching { adminV38(); v38 = v38.copy(budget = v38.budget.filterNot { it.id == id }); saveV38() }
    override suspend fun subscriptions(month: String): Result<List<SubscriptionMark>> = runCatching {
        val u = _user.value ?: throw PermissionDenied()
        if (u.role == Role.ADMIN) v38.subs.filter { it.month == month }
        else if (u.role == Role.TEACHER) { val mine = store.data.circles.filter { it.taughtBy(u.id) }.map { it.id }.toSet()
            v38.subs.filter { it.month == month && store.data.student(it.studentId)?.circleId in mine } }
        else throw PermissionDenied()
    }
    override suspend fun saveSubscription(m: SubscriptionMark): Result<Unit> = runCatching {
        val u = _user.value ?: throw PermissionDenied()
        val s = store.data.student(m.studentId) ?: throw ValidationError("الطالب غير موجود")
        if (!(u.role == Role.ADMIN || (u.role == Role.TEACHER && store.data.circle(s.circleId)?.taughtBy(u.id) == true))) throw PermissionDenied()
        v38 = v38.copy(subs = v38.subs.filterNot { it.studentId == m.studentId && it.month == m.month } + m.copy(amount = if (m.paid) m.amount else null)); saveV38()
    }
    override suspend fun setCirclePhoto(circleId: String, jpeg: ByteArray?) = mutate { d, u ->
        val c = d.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        if (!(u.role == Role.ADMIN || (u.role == Role.TEACHER && c.taughtBy(u.id)))) throw PermissionDenied()
        c.photoPath?.takeIf { !it.startsWith("demo-seed/") }?.let { runCatching { File(photoDir, it.replace('/', '_')).delete() } }
        val path = jpeg?.let { "demo/circles/$circleId/${newId().take(8)}.jpg" }
        if (jpeg != null && path != null) File(photoDir, path.replace('/', '_')).writeBytes(jpeg)
        d.copy(circles = d.circles.map { if (it.id == circleId) it.copy(photoPath = path) else it }) to Unit
    }
    override suspend fun setCircleTeachers(circleId: String, teacherIds: List<String>) = mutate { d, u ->
        if (u.role != Role.ADMIN) throw PermissionDenied()
        d.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        val ids = teacherIds.distinct(); val first = ids.firstOrNull()
        d.copy(circles = d.circles.map { if (it.id == circleId) it.copy(teacherId = first, teacherName = d.member(first)?.fullName, teacherIds = ids) else it }) to Unit
    }
    override suspend fun postAnnouncementWithImage(a: Announcement, jpeg: ByteArray): Result<Announcement> {
        val id = a.id.ifBlank { newId() }
        val path = "demo/announcements/$id/${newId().take(8)}.jpg"
        runCatching { File(photoDir, path.replace('/', '_')).writeBytes(jpeg) }
        return postAnnouncement(a.copy(id = id, imagePath = path))
    }
    override suspend fun circlePhoto(path: String): ByteArray? =
        if (path.startsWith("demo-seed/")) runCatching { context.resources.openRawResource(com.alsiddiq.app.R.drawable.home_bg_blur).use { it.readBytes() } }.getOrNull()
        else studentPhoto(path)

    override suspend fun moveAboutButton(id: String, up: Boolean): Result<Unit> = runCatching { adminOnly(); aboutLocal.set(aboutLocal.page.copy(buttons = AboutLocal.move(aboutLocal.page.buttons, id, up))) }
}
