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
    /** The general (viewer) login kept underneath while a teacher/admin works from Settings. */
    val baseSession: Session? = null,
    val baseMe: Member? = null,
    val centerName: String = "",
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
    private val _base = MutableStateFlow(st.baseMe)
    override val baseUser: StateFlow<Member?> = _base.asStateFlow()
    private val _centerName = MutableStateFlow(st.centerName)
    override val centerName: StateFlow<String> = _centerName.asStateFlow()
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
        _base.value = st.baseMe
        if (st.centerName.isNotBlank()) _centerName.value = st.centerName
        _sync.value = if (st.outbox.isEmpty()) SyncState.Synced else SyncState.Pending(st.outbox.size)
    }

    /** Arabic-Indic digits typed on an Arabic keyboard count as the same password as Latin digits. */
    private fun normPw(p: String) = p.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").trim()

    private suspend fun token(): String {
        val s = st.session ?: throw PermissionDenied("سجّل الدخول أولاً")
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        val fresh = try { api.refresh(s.refreshToken) } catch (e: ApiException) {
            if (e.code in 400..499) {
                // This login ended on the server (its password was changed or the teacher removed).
                lock.withLock {
                    st = if (st.baseSession != null && st.baseSession != s)
                        st.copy(session = st.baseSession, me = st.baseMe, baseSession = null, baseMe = null, cache = CenterSnapshot(center = st.cache.center), outbox = emptyList())
                    else CloudState(centerName = st.centerName)
                    save(); publish()
                }
            }
            throw e
        }
        st = st.copy(session = fresh); save()
        return fresh.accessToken
    }

    // ---------------- first-time setup ----------------
    override suspend fun needsSetup(): Boolean? = runCatching {
        val o = api.function(null, "access", buildJsonObject { put("action", "status") })
        o["center_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { n ->
            lock.withLock { st = st.copy(centerName = n); save() }; _centerName.value = n
        }
        o["needs_setup"]?.jsonPrimitive?.booleanOrNull
    }.getOrNull()

    private fun memberFrom(p: JsonObject) = Member(
        id = p["id"]!!.jsonPrimitive.content,
        fullName = p["full_name"]?.jsonPrimitive?.contentOrNull ?: "",
        phone = "",
        role = when (p["access_kind"]?.jsonPrimitive?.contentOrNull) { "admin" -> Role.ADMIN; "teacher" -> Role.TEACHER; else -> Role.VIEWER }
    )

    private suspend fun accessCall(body: JsonObject, token: String? = null): JsonObject =
        try { api.function(token, "access", body) }
        catch (e: OfflineException) { throw ValidationError("الدخول يحتاج اتصالاً بالإنترنت أول مرة") }

    /** Store a fresh login. [keepBase] = it was opened from Settings over the general view. */
    private suspend fun adopt(o: JsonObject, keepBase: Boolean): Member {
        val s = api.sessionOf(o["session"]!!.jsonObject)
        val me = memberFrom(o["profile"]!!.jsonObject)
        val name = o["center_name"]?.jsonPrimitive?.contentOrNull ?: st.centerName
        lock.withLock {
            val base = st.session?.takeIf { keepBase && st.me?.role == Role.VIEWER && me.role != Role.VIEWER }
            val baseMe = st.me?.takeIf { base != null }
            st = CloudState(session = s, me = me, baseSession = base ?: st.baseSession.takeIf { keepBase }, baseMe = baseMe ?: st.baseMe.takeIf { keepBase },
                centerName = name, centerId = null, outbox = emptyList())
            if (me.role == Role.VIEWER) st = st.copy(baseSession = null, baseMe = null)
            save()
        }
        _centerName.value = name
        val center = runCatching { api.selectAll(s.accessToken, "centers", "select=id&limit=1").firstOrNull()?.get("id")?.jsonPrimitive?.contentOrNull }.getOrNull()
        lock.withLock { st = st.copy(centerId = center); save() }
        runCatching { reload() }
        // Don't raise phone notifications for what was already waiting before this sign-in.
        lock.withLock { st = st.copy(seenNotificationIds = st.cache.notifications.map { it.id }); save() }
        publish()
        return st.me ?: me
    }

    override suspend fun setup(centerName: String, adminPassword: String, viewerPassword: String): Result<Member> = runCatching {
        val a = normPw(adminPassword); val v = normPw(viewerPassword)
        if (a.length < 6 || v.length < 6) throw ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل")
        if (a == v) throw ValidationError("كلمة سر الإدارة يجب أن تختلف عن كلمة السر العامة")
        val o = accessCall(buildJsonObject {
            put("action", "setup"); put("center_name", centerName.trim()); put("admin_password", a); put("viewer_password", v)
        })
        adopt(o, keepBase = false)
    }

    override suspend fun signIn(password: String, kind: Role?): Result<Member> = runCatching {
        val pw = normPw(password)
        if (pw.isEmpty()) throw ValidationError("اكتب كلمة السر")
        val o = accessCall(buildJsonObject {
            put("action", "login"); put("password", pw)
            put("kind", when (kind) { Role.ADMIN -> "admin"; Role.TEACHER -> "teacher"; else -> null })
        })
        adopt(o, keepBase = kind != null)
    }

    override suspend fun leaveRole() {
        val t = st.session?.accessToken
        lock.withLock {
            st = if (st.baseSession != null) st.copy(session = st.baseSession, me = st.baseMe, baseSession = null, baseMe = null,
                cache = CenterSnapshot(center = st.cache.center), outbox = emptyList())
            else CloudState(centerName = st.centerName)
            save(); publish()
        }
        if (t != null) scope.launch { api.signOut(t) }
        if (st.session != null) scope.launch { runCatching { syncNow() } }
    }

    override suspend fun signOut() {
        val ts = listOfNotNull(st.session?.accessToken, st.baseSession?.accessToken)
        lock.withLock { st = CloudState(centerName = st.centerName); save(); publish() }
        ts.forEach { t -> scope.launch { api.signOut(t) } }
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
            val circles = async { api.selectAll(t, "circles", "select=*&order=name") }
            val students = async { api.selectAll(t, "students", "select=*&order=full_name") }
            val recs = async { api.selectAll(t, "recitations", "select=*&order=recorded_at.desc") }
            val att = async { api.selectAll(t, "attendance", "select=*&order=day.desc") }
            val rewards = async { api.selectAll(t, "rewards", "select=*&order=created_at.desc") }
            val ann = async { api.selectAll(t, "announcements", "select=*&order=created_at.desc") }
            val notes = async { api.selectAll(t, "notifications", "select=*&user_id=eq.${me.id}&order=created_at.desc") }
            val members = profiles.await().map { Rows.member(it) }
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
                val meNow = me.copy(fullName = members.firstOrNull { it.id == me.id }?.fullName ?: me.fullName)
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
        try { return api.function(token(), "access", body) }
        catch (e: OfflineException) { throw ValidationError("هذه العملية تحتاج اتصالاً بالإنترنت") }
    }

    override suspend fun saveTeacher(id: String?, name: String, circleId: String?, password: String?): Result<Member> = guarded {
        require(Permissions.canManageMembers(me()))
        if (name.isBlank()) throw ValidationError("اكتب اسم المعلم")
        val pw = password?.let { normPw(it) }?.takeIf { it.isNotEmpty() }
        if (id == null && pw == null) throw ValidationError("اختر كلمة سر للمعلم")
        if (pw != null && pw.length < 6) throw ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل")
        val res = adminFunction(buildJsonObject {
            put("action", "teacher_save"); put("id", id); put("name", name.trim()); put("circle_id", circleId ?: "")
            if (pw != null) put("password", pw)
        })
        val m = memberFrom(res["profile"]!!.jsonObject)
        lock.withLock {
            val d = st.cache
            st = st.copy(cache = d.copy(
                members = if (d.members.any { it.id == m.id }) d.members.map { if (it.id == m.id) m else it } else d.members + m,
                circles = d.circles.map { when { it.id == circleId -> it.copy(teacherId = m.id); it.teacherId == m.id -> it.copy(teacherId = null); else -> it } }
            )); save(); publish()
        }
        scope.launch { runCatching { syncNow() } }
        Result.success(m)
    }

    override suspend fun removeTeacher(id: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        adminFunction(buildJsonObject { put("action", "teacher_remove"); put("id", id) })
        lock.withLock {
            val d = st.cache
            st = st.copy(cache = d.copy(members = d.members.map { if (it.id == id) it.copy(active = false) else it },
                circles = d.circles.map { if (it.teacherId == id) it.copy(teacherId = null) else it })); save(); publish()
        }
        scope.launch { runCatching { syncNow() } }
        Result.success(Unit)
    }

    override suspend fun setAccessPassword(target: Role, password: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        val pw = normPw(password)
        if (pw.length < 6) throw ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل")
        adminFunction(buildJsonObject { put("action", "set_password"); put("target", if (target == Role.ADMIN) "admin" else "viewer"); put("password", pw) })
        Result.success(Unit)
    }

    /** Debug-only (CI): try raw writes past the app's own checks so the server's own answer is logged. */
    suspend fun debugRawWrites(studentId: String, circleId: String?): List<String> {
        val t = token(); val me = me(); val center = center()
        val rec = buildJsonObject {
            put("id", newId()); put("center_id", center); put("student_id", studentId); put("circle_id", circleId); put("teacher_id", me.id)
            put("kind", "hifz"); put("from_surah", 1); put("from_ayah", 1); put("to_surah", 1); put("to_ayah", 7); put("grade", "excellent"); put("notes", "selftest-raw")
        }
        val att = buildJsonObject {
            put("center_id", center); put("student_id", studentId); put("circle_id", circleId); put("day", "2026-01-01"); put("status", "absent"); put("recorded_by", me.id)
        }
        val r1 = api.rawInsert(t, "recitations", rec)
        val r2 = api.rawInsert(t, "attendance", att)
        val r3 = api.rawPatch(t, "students", "id=eq.$studentId", buildJsonObject { put("notes", "selftest-edit-by-" + me.role.name.lowercase()) })
        return listOf("recitation=${r1.first}", "attendance=${r2.first}", "student_edit=${r3.first}:${if (r3.second.trim() == "[]") "0rows" else "changed"}")
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
        val existing = st.cache.student(s.id)
        require(if (existing == null) Permissions.canAddStudentTo(me(), s.circleId, st.cache)
                else Permissions.canEditStudent(me(), existing, st.cache) && Permissions.canAddStudentTo(me(), s.circleId, st.cache))
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
