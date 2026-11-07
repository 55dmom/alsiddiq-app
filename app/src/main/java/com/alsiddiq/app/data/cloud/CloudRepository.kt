package com.alsiddiq.app.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.alsiddiq.app.data.*
import com.alsiddiq.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** One write waiting to reach the server (kept on disk, so it survives app restarts and no network). */
@Serializable
data class PendingOp(
    val id: String = UUID.randomUUID().toString(),
    val type: String,            // upsert | patch | delete
    val table: String,
    val body: String = "",
    val filter: String = "",
    val onConflict: String = "id",
    val ignoreDuplicates: Boolean = false,
    val label: String = ""
)

@Serializable
private data class CloudState(
    val session: Session? = null,
    val me: Member? = null,
    val centerId: String? = null,
    val cache: CenterSnapshot = CenterSnapshot(),
    val outbox: List<PendingOp> = emptyList(),
    val lastSyncAt: Long = 0,
    val seenNotificationIds: List<String> = emptyList()
)

/**
 * The real backend: central Postgres database (Supabase) with server-side security rules.
 * Reads come from the server and are cached on the phone for offline use; writes are applied
 * on screen at once, queued on disk, and sent as soon as there is a connection.
 */
class CloudRepository(private val context: Context, url: String, publicKey: String) : AuthRepository, CenterRepository {
    private val api = SupabaseApi(url, publicKey)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "cloud_state.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val syncLock = Mutex()

    private var st: CloudState = runCatching { json.decodeFromString(CloudState.serializer(), file.readText()) }.getOrDefault(CloudState())

    override val mode = BackendMode.CLOUD
    private val _user = MutableStateFlow(st.me)
    override val currentUser: StateFlow<Member?> = _user.asStateFlow()
    private val _snapshot = MutableStateFlow(st.cache)
    override val snapshot: StateFlow<CenterSnapshot> = _snapshot.asStateFlow()
    private val _sync = MutableStateFlow<SyncState>(if (st.outbox.isEmpty()) SyncState.Synced else SyncState.Pending(st.outbox.size))
    override val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    init {
        // Sync loop while the app process lives: every 30 s, and immediately when the network comes back.
        scope.launch {
            while (isActive) {
                if (st.session != null) runCatching { syncNow() }
                delay(30_000)
            }
        }
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { scope.launch { if (st.session != null) runCatching { syncNow() } } }
            })
        }
    }

    private fun save() {
        val text = json.encodeToString(CloudState.serializer(), st)
        runCatching { file.writeText(text) }
    }

    private fun publish() {
        _snapshot.value = st.cache
        _user.value = st.me
        _sync.value = if (st.outbox.isEmpty()) SyncState.Synced else SyncState.Pending(st.outbox.size)
    }

    private fun digits(p: String) = p.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.filter { it.isDigit() }.joinToString("")
    private fun emailFor(phone: String) = digits(phone) + "@alsiddiq.app"

    private suspend fun token(): String {
        val s = st.session ?: throw PermissionDenied("سجّل الدخول أولاً")
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        val fresh = try { api.refresh(s.refreshToken) } catch (e: ApiException) {
            if (e.code in 400..499) { lock.withLock { st = CloudState(); save(); publish() } }
            throw e
        }
        st = st.copy(session = fresh); save()
        return fresh.accessToken
    }

    // ---------------- first-time setup ----------------
    override suspend fun needsSetup(): Boolean? = runCatching {
        api.function(null, "admin-members", buildJsonObject { put("action", "status") })["needs_setup"]?.jsonPrimitive?.booleanOrNull
    }.getOrNull()

    override suspend fun bootstrap(centerName: String, fullName: String, phone: String, password: String): Result<Member> = runCatching {
        api.function(null, "admin-members", buildJsonObject {
            put("action", "bootstrap"); put("center_name", centerName); put("full_name", fullName)
            put("phone", digits(phone)); put("password", password)
        })
        signIn(phone, password).getOrThrow()
    }

    // ---------------- auth ----------------
    override suspend fun signIn(login: String, password: String): Result<Member> = runCatching {
        if (digits(login).length < 8) throw ValidationError("رقم الجوال غير صحيح")
        val s = try { api.signIn(emailFor(login), password) } catch (e: OfflineException) { throw ValidationError("لا يوجد اتصال بالإنترنت") }
        val prof = api.selectAll(s.accessToken, "profiles", "select=*&id=eq.${s.userId}").firstOrNull()
            ?: throw ValidationError("الحساب غير مكتمل، راجع إدارة المركز")
        val me = Rows.member(prof, emptyMap())
        if (!me.active) throw ValidationError("هذا الحساب موقوف، راجع إدارة المركز")
        lock.withLock {
            st = CloudState(session = s, me = me, centerId = prof["center_id"]?.jsonPrimitive?.contentOrNull)
            save()
        }
        runCatching { reload() }
        // Don't raise phone notifications for what was already waiting before this sign-in.
        lock.withLock { st = st.copy(seenNotificationIds = st.cache.notifications.map { it.id }); save() }
        publish()
        st.me ?: me
    }

    override suspend fun signOut() {
        val t = st.session?.accessToken
        lock.withLock { st = CloudState(); save(); publish() }
        if (t != null) scope.launch { api.signOut(t) }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = runCatching {
        val me = st.me ?: throw PermissionDenied()
        if (newPassword.length < 6) throw ValidationError("كلمة المرور 6 أحرف على الأقل")
        try { api.signIn(emailFor(me.phone), oldPassword) } catch (e: ApiException) { throw ValidationError("كلمة المرور الحالية غير صحيحة") }
        api.updatePassword(token(), newPassword)
    }

    // ---------------- sync ----------------
    override suspend fun refresh(): Result<Unit> = runCatching { syncNow() }

    private suspend fun syncNow() = syncLock.withLock {
        flushOutbox()
        reload()
    }

    /** Pull everything this account may see. RLS on the server decides what that is. */
    private suspend fun reload() {
        val t = token()
        val me = st.me ?: return
        coroutineScope {
            val centers = async { api.selectAll(t, "centers") }
            val profiles = async { api.selectAll(t, "profiles") }
            val links = async { api.selectAll(t, "parent_students") }
            val circles = async { api.selectAll(t, "circles", "select=*&order=name") }
            val students = async { api.selectAll(t, "students", "select=*&order=full_name") }
            val recs = async { api.selectAll(t, "recitations", "select=*&order=recorded_at.desc") }
            val att = async { api.selectAll(t, "attendance", "select=*&order=day.desc") }
            val rewards = async { api.selectAll(t, "rewards", "select=*&order=created_at.desc") }
            val ann = async { api.selectAll(t, "announcements", "select=*&order=created_at.desc") }
            val notes = async { api.selectAll(t, "notifications", "select=*&user_id=eq.${me.id}&order=created_at.desc") }
            val children = links.await().groupBy({ it["parent_id"]!!.jsonPrimitive.content }, { it["student_id"]!!.jsonPrimitive.content })
            val members = profiles.await().map { Rows.member(it, children) }
            val fresh = CenterSnapshot(
                center = centers.await().firstOrNull()?.let { Rows.center(it) } ?: CenterInfo(),
                members = members,
                circles = circles.await().map { Rows.circle(it) },
                students = students.await().map { Rows.student(it) },
                recitations = recs.await().map { Rows.recitation(it) },
                attendance = att.await().map { Rows.attendance(it) },
                rewards = rewards.await().map { Rows.reward(it) },
                announcements = ann.await().map { Rows.announcement(it) },
                notifications = notes.await().map { Rows.notification(it) }
            )
            lock.withLock {
                // Writes still waiting stay visible on screen until the server has them.
                val merged = if (st.outbox.isEmpty()) fresh else overlayPending(fresh)
                val meNow = members.firstOrNull { it.id == me.id } ?: me
                st = st.copy(cache = merged, me = meNow, lastSyncAt = System.currentTimeMillis())
                save(); publish()
            }
        }
    }

    private fun overlayPending(fresh: CenterSnapshot): CenterSnapshot {
        val pendingIds = st.outbox.mapNotNull { op -> runCatching { json.parseToJsonElement(op.body).jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() }.toSet()
        val old = st.cache
        return fresh.copy(
            recitations = fresh.recitations + old.recitations.filter { it.id in pendingIds && fresh.recitations.none { f -> f.id == it.id } },
            rewards = fresh.rewards + old.rewards.filter { it.id in pendingIds && fresh.rewards.none { f -> f.id == it.id } },
            students = fresh.students + old.students.filter { it.id in pendingIds && fresh.students.none { f -> f.id == it.id } },
            circles = fresh.circles + old.circles.filter { it.id in pendingIds && fresh.circles.none { f -> f.id == it.id } }
        )
    }

    private suspend fun flushOutbox() {
        while (true) {
            val op = st.outbox.firstOrNull() ?: return
            try {
                send(op)
            } catch (e: OfflineException) {
                throw e
            } catch (e: ApiException) {
                if (e.code >= 500) throw e
                // The server refused it (rules or data): drop it so the queue never jams.
            }
            lock.withLock { st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }); save(); publish() }
        }
    }

    private suspend fun send(op: PendingOp) {
        val t = token()
        when (op.type) {
            "upsert" -> api.upsert(t, op.table, json.parseToJsonElement(op.body), op.onConflict, op.ignoreDuplicates)
            "patch" -> api.patch(t, op.table, op.filter, json.parseToJsonElement(op.body))
            "delete" -> api.delete(t, op.table, op.filter)
        }
    }

    /**
     * Apply a change on screen, queue it, and try to send it now.
     * Online and refused by the server -> the change is undone and the reason returned.
     * Offline -> kept in the queue and sent automatically later.
     */
    private suspend fun <T> write(op: PendingOp, value: T, local: (CenterSnapshot) -> CenterSnapshot): Result<T> {
        lock.withLock {
            st = st.copy(cache = local(st.cache), outbox = st.outbox + op); save(); publish()
        }
        return try {
            syncLock.withLock {
                // Send everything queued before this one too, in order.
                flushOutboxStrict(op.id)
            }
            scope.launch { runCatching { syncNow() } }
            Result.success(value)
        } catch (e: OfflineException) {
            Result.success(value)
        } catch (e: ApiException) {
            lock.withLock { st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }); save() }
            runCatching { syncNow() }
            publish()
            Result.failure(e)
        }
    }

    private suspend fun flushOutboxStrict(targetId: String) {
        while (true) {
            val op = st.outbox.firstOrNull() ?: return
            if (op.id == targetId) {
                send(op) // let its ApiException reach the caller
                lock.withLock { st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }); save(); publish() }
                return
            }
            try { send(op) } catch (e: ApiException) { if (e.code >= 500) throw e }
            lock.withLock { st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }); save(); publish() }
        }
    }

    private fun me(): Member = st.me ?: throw PermissionDenied("سجّل الدخول أولاً")
    private fun center(): String = st.centerId ?: throw PermissionDenied("سجّل الدخول أولاً")
    private fun require(ok: Boolean) { if (!ok) throw PermissionDenied() }
    private fun newId() = UUID.randomUUID().toString()

    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> = try { block() } catch (e: Exception) { Result.failure(e) }

    // ---------------- admin ----------------
    override suspend fun updateCenter(info: CenterInfo): Result<Unit> = guarded {
        require(Permissions.canManageCenter(me()))
        write(PendingOp(type = "patch", table = "centers", filter = "id=eq.${center()}",
            body = buildJsonObject { put("name", info.name); put("city", info.city) }.toString()), Unit) { it.copy(center = info) }
    }

    private suspend fun adminFunction(body: JsonObject): JsonObject {
        try { return api.function(token(), "admin-members", body) }
        catch (e: OfflineException) { throw ValidationError("إدارة الحسابات تحتاج اتصالاً بالإنترنت") }
    }

    override suspend fun createMember(m: NewMember): Result<Member> = guarded {
        require(Permissions.canManageMembers(me()))
        if (m.fullName.isBlank()) throw ValidationError("اكتب الاسم")
        if (digits(m.phone).length < 8) throw ValidationError("رقم الجوال غير صحيح")
        if (m.password.length < 6) throw ValidationError("كلمة المرور 6 أحرف على الأقل")
        val res = adminFunction(buildJsonObject {
            put("action", "create"); put("full_name", m.fullName.trim()); put("phone", digits(m.phone))
            put("role", m.role.name.lowercase()); put("password", m.password); put("student_id", m.studentId)
            putJsonArray("child_ids") { m.childIds.forEach { add(it) } }
        })
        val prof = res["profile"]?.jsonObject ?: throw ValidationError("تعذّر إنشاء الحساب")
        val member = Rows.member(prof, mapOf(prof["id"]!!.jsonPrimitive.content to m.childIds))
        lock.withLock { st = st.copy(cache = st.cache.copy(members = st.cache.members + member)); save(); publish() }
        scope.launch { runCatching { syncNow() } }
        Result.success(member)
    }

    override suspend fun updateMember(m: Member): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        adminFunction(buildJsonObject {
            put("action", "update"); put("id", m.id); put("full_name", m.fullName); put("phone", digits(m.phone))
            put("active", m.active); put("student_id", m.studentId)
            putJsonArray("child_ids") { m.childIds.forEach { add(it) } }
        })
        lock.withLock { st = st.copy(cache = st.cache.copy(members = st.cache.members.map { if (it.id == m.id) m else it })); save(); publish() }
        scope.launch { runCatching { syncNow() } }
        Result.success(Unit)
    }

    override suspend fun resetMemberPassword(memberId: String, newPassword: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        if (newPassword.length < 6) throw ValidationError("كلمة المرور 6 أحرف على الأقل")
        adminFunction(buildJsonObject { put("action", "reset_password"); put("id", memberId); put("password", newPassword) })
        Result.success(Unit)
    }

    override suspend fun saveCircle(c: Circle): Result<Circle> = guarded {
        require(Permissions.canManageCircles(me()))
        if (c.name.isBlank()) throw ValidationError("اكتب اسم الحلقة")
        val saved = if (c.id.isBlank()) c.copy(id = newId()) else c
        write(PendingOp(type = "upsert", table = "circles", body = Rows.circleRow(saved, center()).toString()), saved) { d ->
            d.copy(circles = if (d.circles.any { it.id == saved.id }) d.circles.map { if (it.id == saved.id) saved else it } else d.circles + saved)
        }
    }

    override suspend fun deleteCircle(id: String): Result<Unit> = guarded {
        require(Permissions.canManageCircles(me()))
        write(PendingOp(type = "delete", table = "circles", filter = "id=eq.$id"), Unit) { d ->
            d.copy(circles = d.circles.filterNot { it.id == id }, students = d.students.map { if (it.circleId == id) it.copy(circleId = null) else it })
        }
    }

    override suspend fun saveStudent(s: Student): Result<Student> = guarded {
        require(Permissions.canManageStudents(me()))
        if (s.fullName.isBlank()) throw ValidationError("اكتب اسم الطالب")
        val saved = if (s.id.isBlank()) s.copy(id = newId()) else s
        write(PendingOp(type = "upsert", table = "students", body = Rows.studentRow(saved, center()).toString()), saved) { d ->
            d.copy(students = if (d.students.any { it.id == saved.id }) d.students.map { if (it.id == saved.id) saved else it } else d.students + saved)
        }
    }

    override suspend fun deleteStudent(id: String): Result<Unit> = guarded {
        require(Permissions.canManageStudents(me()))
        write(PendingOp(type = "delete", table = "students", filter = "id=eq.$id"), Unit) { d ->
            d.copy(students = d.students.filterNot { it.id == id }, recitations = d.recitations.filterNot { it.studentId == id },
                attendance = d.attendance.filterNot { it.studentId == id }, rewards = d.rewards.filterNot { it.studentId == id })
        }
    }

    // ---------------- teaching ----------------
    override suspend fun logRecitation(r: Recitation): Result<Recitation> = guarded {
        val u = me(); val d = st.cache
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (!com.alsiddiq.app.core.Quran.isValid(r.fromSurah, r.fromAyah) || !com.alsiddiq.app.core.Quran.isValid(r.toSurah, r.toAyah)) throw ValidationError("رقم الآية غير صحيح")
        if (com.alsiddiq.app.core.Quran.ayatBetween(r.fromSurah, r.fromAyah, r.toSurah, r.toAyah) <= 0) throw ValidationError("نهاية المقطع قبل بدايته")
        val saved = r.copy(id = r.id.ifBlank { newId() }, teacherId = u.id, circleId = student.circleId)
        write(PendingOp(type = "upsert", table = "recitations", ignoreDuplicates = true, body = Rows.recitationRow(saved, center()).toString()), saved) {
            it.copy(recitations = listOf(saved) + it.recitations)
        }
    }

    override suspend fun deleteRecitation(id: String): Result<Unit> = guarded {
        write(PendingOp(type = "delete", table = "recitations", filter = "id=eq.$id"), Unit) { d -> d.copy(recitations = d.recitations.filterNot { it.id == id }) }
    }

    override suspend fun saveAttendance(circleId: String, day: String, marks: Map<String, AttendanceStatus>): Result<Int> = guarded {
        val u = me(); val d = st.cache
        val circle = d.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        require(u.role == Role.ADMIN || Permissions.teachesCircle(u, circle))
        val rows = buildJsonArray {
            marks.forEach { (sid, s) -> addJsonObject {
                put("center_id", center()); put("student_id", sid); put("circle_id", circleId); put("day", day)
                put("status", s.name.lowercase()); put("recorded_by", u.id); put("recorded_at", Rows.iso(System.currentTimeMillis()))
            } }
        }
        write(PendingOp(type = "upsert", table = "attendance", onConflict = "student_id,day", body = rows.toString()), marks.size) { snap ->
            val kept = snap.attendance.filterNot { it.day == day && it.studentId in marks.keys }
            snap.copy(attendance = kept + marks.map { (sid, s) -> Attendance(newId(), sid, circleId, day, s, u.id) })
        }
    }

    override suspend fun giveReward(r: Reward): Result<Reward> = guarded {
        val u = me(); val d = st.cache
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (r.points == 0) throw ValidationError("حدّد عدد النقاط")
        val saved = r.copy(id = r.id.ifBlank { newId() }, awardedBy = u.id)
        write(PendingOp(type = "upsert", table = "rewards", ignoreDuplicates = true, body = Rows.rewardRow(saved, center()).toString()), saved) {
            it.copy(rewards = listOf(saved) + it.rewards)
        }
    }

    // ---------------- communication ----------------
    override suspend fun postAnnouncement(a: Announcement): Result<Announcement> = guarded {
        val u = me()
        require(Permissions.canPostAnnouncement(u))
        if (a.title.isBlank()) throw ValidationError("اكتب عنوان الإعلان")
        val saved = a.copy(id = a.id.ifBlank { newId() }, createdBy = u.id)
        write(PendingOp(type = "upsert", table = "announcements", ignoreDuplicates = true, body = Rows.announcementRow(saved, center()).toString()), saved) {
            it.copy(announcements = listOf(saved) + it.announcements)
        }
    }

    override suspend fun deleteAnnouncement(id: String): Result<Unit> = guarded {
        write(PendingOp(type = "delete", table = "announcements", filter = "id=eq.$id"), Unit) { d -> d.copy(announcements = d.announcements.filterNot { it.id == id }) }
    }

    override suspend fun markNotificationRead(id: String): Result<Unit> = guarded {
        write(PendingOp(type = "patch", table = "notifications", filter = "id=eq.$id",
            body = buildJsonObject { put("read_at", Rows.iso(System.currentTimeMillis())) }.toString()), Unit) { d ->
            d.copy(notifications = d.notifications.map { if (it.id == id) it.copy(read = true) else it })
        }
    }

    override suspend fun markAllNotificationsRead(): Result<Unit> = guarded {
        val u = me()
        write(PendingOp(type = "patch", table = "notifications", filter = "user_id=eq.${u.id}&read_at=is.null",
            body = buildJsonObject { put("read_at", Rows.iso(System.currentTimeMillis())) }.toString()), Unit) { d ->
            d.copy(notifications = d.notifications.map { it.copy(read = true) })
        }
    }

    // ---------------- background notifications ----------------
    /** New unread notifications since the last check (used by the background worker). */
    suspend fun fetchNewNotifications(): List<AppNotification> {
        val u = st.me ?: return emptyList()
        val rows = api.selectAll(token(), "notifications", "select=*&user_id=eq.${u.id}&read_at=is.null&order=created_at.desc").map { Rows.notification(it) }
        val seen = st.seenNotificationIds.toSet()
        val fresh = rows.filter { it.id !in seen }
        lock.withLock { st = st.copy(seenNotificationIds = (rows.map { it.id } + st.seenNotificationIds).distinct().take(500)); save() }
        return fresh
    }

    fun isSignedIn() = st.session != null
}
