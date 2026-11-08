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
    val label: String = "",
    /** v3.2: the account that made this change (its own login sends it), when it was made on the phone,
     *  the rows it touches (for «بانتظار الإرسال»), and for a photo the local file waiting to upload. */
    val owner: String = "",
    val at: Long = System.currentTimeMillis(),
    val keys: List<String> = emptyList(),
    val file: String? = null,
    val reason: String? = null
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
    val seenNotificationIds: List<String> = emptyList(),
    /** v3.2: logins kept only to send the changes their owner left waiting (after «خروج» or sign-out). */
    val held: Map<String, Session> = emptyMap(),
    val heldMembers: Map<String, Member> = emptyMap(),
    /** v3.2: owners whose login ended with changes still waiting: ask for the password again, then send. */
    val needsReauth: List<String> = emptyList(),
    /** v3.2: changes the server refused (with its reason) — kept, never silently dropped. */
    val rejected: List<PendingOp> = emptyList(),
    val superseded: Int = 0
)

/** v3.2: the login that owns a waiting change has ended; the change stays queued. */
class NeedsReauth(val owner: String) : Exception("انتهت الجلسة، أدخل كلمة السر مرة ثانية")
private class Superseded : Exception("superseded")

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
        .let { s -> if (s.outbox.any { it.owner.isBlank() }) s.copy(outbox = s.outbox.map { if (it.owner.isBlank()) it.copy(owner = s.me?.id ?: "") else it }) else s }

    override val mode = BackendMode.CLOUD
    private val _user = MutableStateFlow(st.me)
    override val currentUser: StateFlow<Member?> = _user.asStateFlow()
    private val _base = MutableStateFlow(st.baseMe)
    override val baseUser: StateFlow<Member?> = _base.asStateFlow()
    private val _centerName = MutableStateFlow(st.centerName)
    override val centerName: StateFlow<String> = _centerName.asStateFlow()
    private val _snapshot = MutableStateFlow(st.cache)
    override val snapshot: StateFlow<CenterSnapshot> = _snapshot.asStateFlow()
    private val _sync = MutableStateFlow<SyncState>(syncOf(st))
    override val syncState: StateFlow<SyncState> = _sync.asStateFlow()
    private val _pendingKeys = MutableStateFlow(keysOf(st))
    override val pendingKeys: StateFlow<Set<String>> = _pendingKeys.asStateFlow()
    private val _outboxInfo = MutableStateFlow(infoOf(st))
    override val outboxInfo: StateFlow<OutboxInfo> = _outboxInfo.asStateFlow()

    private fun syncOf(s: CloudState): SyncState = if (s.outbox.isEmpty()) SyncState.Synced else SyncState.Pending(s.outbox.size)
    private fun keysOf(s: CloudState): Set<String> = s.outbox.flatMap { it.keys }.toSet()
    private fun infoOf(s: CloudState) = OutboxInfo(
        pending = s.outbox.size,
        needsPasswordFor = s.needsReauth.mapNotNull { id -> (if (id == s.me?.id) s.me else s.heldMembers[id] ?: s.baseMe?.takeIf { it.id == id }) }
            .map { ReauthTarget(it.id, it.fullName, it.role, s.outbox.count { op -> op.owner == it.id }) },
        rejected = s.rejected.map { RejectedChange(it.id, it.label.ifBlank { it.table }, it.reason ?: "") },
        lastSyncAt = s.lastSyncAt
    )

    init {
        // Sync loop while the app process lives: every 30 s, and immediately when the network comes back.
        scope.launch {
            while (isActive) {
                if (st.session != null || st.outbox.isNotEmpty()) runCatching { syncNow() }
                delay(30_000)
            }
        }
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { scope.launch { if (st.session != null || st.outbox.isNotEmpty()) runCatching { syncNow() } } }
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
        _sync.value = syncOf(st)
        _pendingKeys.value = keysOf(st)
        _outboxInfo.value = infoOf(st)
    }

    /** Arabic-Indic digits typed on an Arabic keyboard count as the same password as Latin digits. */
    private fun normPw(p: String) = p.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").trim()

    private suspend fun token(): String {
        val s = st.session ?: throw PermissionDenied("سجّل الدخول أولاً")
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        val fresh = try { api.refresh(s.refreshToken) } catch (e: ApiException) {
            if (e.code in 400..499) {
                // This login ended on the server (its password was changed or the teacher stopped).
                // v3.2: its waiting changes are NOT dropped: they stay queued until the password is entered again.
                val me = st.me
                val waiting = me != null && st.outbox.any { it.owner == me.id }
                android.util.Log.i("ALSIDDIQ_SELFTEST", "login ended on the server (refresh ${e.code}) -> " + (if (waiting) "changes kept, asking for the password; " else "") + "back to " + (if (st.baseSession != null && st.baseSession != s) "the general view" else "the password screen"))
                lock.withLock {
                    val keep = st.copy(
                        heldMembers = if (waiting && me != null) st.heldMembers + (me.id to me) else st.heldMembers,
                        held = if (me != null) st.held - me.id else st.held,
                        needsReauth = if (waiting && me != null) (st.needsReauth + me.id).distinct() else st.needsReauth)
                    st = if (st.baseSession != null && st.baseSession != s)
                        keep.copy(session = st.baseSession, me = st.baseMe, baseSession = null, baseMe = null)
                    else keep.copy(session = null, me = null, baseSession = null, baseMe = null)
                    save(); publish()
                }
                if (waiting && me != null) throw NeedsReauth(me.id)
            }
            throw e
        }
        lock.withLock { st = st.copy(session = fresh); save() }
        return fresh.accessToken
    }

    /** v3.2: the token of the account that owns a waiting change (the current one, the base, or a held login). */
    private suspend fun tokenFor(owner: String): String {
        if (owner in st.needsReauth) throw NeedsReauth(owner)
        if (owner.isBlank() || owner == st.me?.id) return token()
        val base = owner == st.baseMe?.id
        val s = (if (base) st.baseSession else st.held[owner]) ?: throw NeedsReauth(owner)
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        val fresh = try { api.refresh(s.refreshToken) } catch (e: ApiException) {
            if (e.code in 400..499) throw NeedsReauth(owner)
            throw e
        }
        lock.withLock { st = if (base) st.copy(baseSession = fresh) else st.copy(held = st.held + (owner to fresh)); save() }
        return fresh.accessToken
    }

    private suspend fun markReauth(owner: String) = lock.withLock {
        val m = if (owner == st.me?.id) st.me else st.heldMembers[owner] ?: st.baseMe?.takeIf { it.id == owner }
        st = st.copy(needsReauth = (st.needsReauth + owner).distinct(),
            heldMembers = if (m != null) st.heldMembers + (owner to m) else st.heldMembers)
        save(); publish()
    }

    /** v3.2: enter the password again for changes left waiting by [owner]; they are sent right away. */
    override suspend fun reauthenticate(owner: String, password: String): Result<Int> = runCatching {
        val m = st.heldMembers[owner] ?: st.me?.takeIf { it.id == owner } ?: throw ValidationError("لا توجد تعديلات بانتظار هذا الحساب")
        val o = accessCall(buildJsonObject {
            put("action", "login"); put("password", normPw(password))
            put("kind", when (m.role) { Role.ADMIN -> "admin"; Role.TEACHER -> "teacher"; else -> "viewer" })
        })
        val who = memberFrom(o["profile"]!!.jsonObject)
        if (who.id != owner) {
            // Someone else's password: don't send his waiting changes under another account.
            runCatching { api.signOut(api.sessionOf(o["session"]!!.jsonObject).accessToken) }
            throw ValidationError("هذي كلمة سر حساب ثاني. أدخل كلمة سر ${m.fullName}")
        }
        val s = api.sessionOf(o["session"]!!.jsonObject)
        lock.withLock {
            st = if (owner == st.me?.id) st.copy(session = s) else if (owner == st.baseMe?.id) st.copy(baseSession = s) else st.copy(held = st.held + (owner to s))
            st = st.copy(needsReauth = st.needsReauth - owner); save(); publish()
        }
        val before = st.outbox.count { it.owner == owner }
        runCatching { syncNow() }
        before - st.outbox.count { it.owner == owner }
    }

    /** v3.2: the user chose to throw away a change the server refused. */
    override suspend fun dismissRejected(id: String?) {
        lock.withLock { st = st.copy(rejected = if (id == null) emptyList() else st.rejected.filterNot { it.id == id }); save(); publish() }
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
        role = when (p["access_kind"]?.jsonPrimitive?.contentOrNull) { "admin" -> Role.ADMIN; "teacher", "teachers" -> Role.TEACHER; else -> Role.VIEWER },
        allCircles = p["access_kind"]?.jsonPrimitive?.contentOrNull == "teachers" || p["all_circles"]?.jsonPrimitive?.booleanOrNull == true
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
            // v3.2: the waiting changes, the logins kept to send them and the cache all survive a new sign-in.
            val leaving = st.me?.takeIf { it.id != me.id && it.id != baseMe?.id && st.outbox.any { op -> op.owner == it.id } }
            val held0 = if (leaving != null && st.session != null) st.held + (leaving.id to st.session!!) else st.held
            val heldM0 = if (leaving != null) st.heldMembers + (leaving.id to leaving) else st.heldMembers
            st = st.copy(session = s, me = me, baseSession = base ?: st.baseSession.takeIf { keepBase }, baseMe = baseMe ?: st.baseMe.takeIf { keepBase },
                centerName = name, centerId = st.centerId,
                held = held0 - me.id, heldMembers = heldM0 - me.id, needsReauth = st.needsReauth - me.id)
            if (me.role == Role.VIEWER) st = st.copy(baseSession = null, baseMe = null)
            save()
        }
        _centerName.value = name
        val center = runCatching { api.selectAll(s.accessToken, "centers", "select=id&limit=1").firstOrNull()?.get("id")?.jsonPrimitive?.contentOrNull }.getOrNull()
        lock.withLock { st = st.copy(centerId = center ?: st.centerId); save() }
        runCatching { syncNow() }
        // Don't raise phone notifications for what was already waiting before this sign-in.
        lock.withLock { st = st.copy(seenNotificationIds = st.cache.notifications.map { it.id } + st.cache.events.map { "ev:" + it.id }); save() }
        publish()
        return st.me ?: me
    }

    override suspend fun setup(centerName: String, adminPassword: String, viewerPassword: String): Result<Member> = runCatching {
        // v3.3: admin + general passwords only; each teacher gets his own password from the admin later.
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
        var keepLogin = false
        lock.withLock {
            val me = st.me; val s = st.session
            // v3.2: changes still waiting keep their owner's login (until sent), so «خروج» never loses them.
            keepLogin = me != null && s != null && st.outbox.any { it.owner == me.id }
            val held = if (keepLogin) st.held + (me!!.id to s!!) else st.held
            val heldM = if (keepLogin) st.heldMembers + (me!!.id to me) else st.heldMembers
            st = if (st.baseSession != null) st.copy(session = st.baseSession, me = st.baseMe, baseSession = null, baseMe = null, held = held, heldMembers = heldM)
            else st.copy(session = null, me = null, baseSession = null, baseMe = null, held = held, heldMembers = heldM)
            save(); publish()
        }
        if (t != null && !keepLogin) scope.launch { api.signOut(t) }
        if (st.session != null || st.outbox.isNotEmpty()) scope.launch { runCatching { syncNow() } }
    }

    override suspend fun signOut() {
        val toEnd = mutableListOf<String>()
        lock.withLock {
            var held = st.held; var heldM = st.heldMembers
            listOfNotNull(st.me?.let { it to st.session }, st.baseMe?.let { it to st.baseSession }).forEach { (m, s) ->
                if (s == null) return@forEach
                if (st.outbox.any { it.owner == m.id }) { held = held + (m.id to s); heldM = heldM + (m.id to m) } else toEnd += s.accessToken
            }
            // v3.2: signing out keeps the waiting changes (sent by their own login in the background) and the offline copy.
            st = CloudState(centerName = st.centerName, centerId = st.centerId, cache = st.cache, outbox = st.outbox, held = held, heldMembers = heldM,
                needsReauth = st.needsReauth, rejected = st.rejected, superseded = st.superseded, lastSyncAt = st.lastSyncAt)
            save(); publish()
        }
        toEnd.forEach { t -> scope.launch { api.signOut(t) } }
    }

    // ---------------- sync ----------------
    override suspend fun refresh(): Result<Unit> = runCatching { syncNow() }

    private suspend fun syncNow() = syncLock.withLock {
        flushOutbox()
        if (st.session != null) reload()
    }

    /** v3.2: called by the background sync worker (network constraint) and on app open. */
    suspend fun backgroundSync(): Boolean {
        if (st.session == null && st.outbox.isEmpty()) return true
        return try { syncNow(); true } catch (e: OfflineException) { false } catch (e: ApiException) { e.code !in 500..599 }
    }
    fun hasWaiting() = st.outbox.isNotEmpty()

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
            val names = async { runCatching { api.selectAll(t, "teacher_names", "select=*&order=name") }.getOrDefault(emptyList()) }
            val honors = async { runCatching { api.selectAll(t, "honor_cards", "select=*&order=created_at.desc") }.getOrDefault(emptyList()) }
            // v3.8: more than one teacher per circle (an older server without the table = the single link only).
            val circleTeachers = async { runCatching { api.selectAll(t, "circle_teachers", "select=circle_id,teacher_profile_id&order=created_at") }.getOrDefault(emptyList()) }
            // v3.6: only the admin reads the teachers' actions.
            val events = async { if (me.role != Role.ADMIN) emptyList() else runCatching { api.selectAll(t, "admin_events", "select=*&order=created_at.desc") }.getOrDefault(emptyList()) }
            val members = profiles.await().map { Rows.member(it) }.map { if (it.id == me.id) it.copy(allCircles = me.allCircles) else it }
            if (members.none { it.id == me.id }) {
                // v3.1: my own profile is unreadable = this login was ended on the server (password changed).
                // Force a refresh: a 4xx there drops this login (see token()).
                lock.withLock { st.session?.let { s -> st = st.copy(session = s.copy(expiresAt = 0)) }; save() }
                token()
                return@coroutineScope
            }
            val allStudents = students.await().map { Rows.student(it) }
            val fresh = CenterSnapshot(
                center = centers.await().firstOrNull()?.let { Rows.center(it) } ?: CenterInfo(),
                members = members,
                circles = circleTeachers.await().groupBy({ it["circle_id"]?.jsonPrimitive?.contentOrNull }, { it["teacher_profile_id"]?.jsonPrimitive?.contentOrNull }).let { ct ->
                    circles.await().map { Rows.circle(it) }.map { c -> c.copy(teacherIds = ct[c.id].orEmpty().filterNotNull()) } },
                students = allStudents.filter { it.active },
                dismissed = allStudents.filterNot { it.active },
                events = events.await().map { Rows.event(it) },
                honorCards = honors.await().map { Rows.honorCard(it) },
                recitations = recs.await().map { Rows.recitation(it) },
                attendance = att.await().map { Rows.attendance(it) },
                rewards = rewards.await().map { Rows.reward(it) },
                announcements = ann.await().map { Rows.announcement(it) },
                teacherNames = names.await().map { Rows.teacherName(it) },
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

    /** Changes still waiting stay on screen (with «بانتظار الإرسال») over what the server sent. */
    private fun overlayPending(fresh: CenterSnapshot): CenterSnapshot {
        val keys = st.outbox.flatMap { it.keys }.toSet() + st.outbox.mapNotNull { op -> runCatching { json.parseToJsonElement(op.body).jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
        val old = st.cache
        fun <T> over(freshL: List<T>, oldL: List<T>, id: (T) -> String): List<T> {
            val mine = oldL.filter { id(it) in keys }.associateBy(id)
            return freshL.map { mine[id(it)] ?: it } + mine.values.filter { m -> freshL.none { id(it) == id(m) } }
        }
        val deleted = st.outbox.filter { it.type == "delete" }.mapNotNull { Regex("id=eq\\.([^&]+)").find(it.filter)?.groupValues?.get(1) }.toSet()
        // v3.6: a «فصل» / «إرجاع» still waiting keeps the student where the user put him.
        fun activeSet(v: Boolean) = st.outbox.filter { it.type == "patch" && it.table == "students" && runCatching { json.parseToJsonElement(it.body).jsonObject["active"]?.jsonPrimitive?.booleanOrNull == v }.getOrDefault(false) }
            .mapNotNull { Regex("id=eq\\.([^&]+)").find(it.filter)?.groupValues?.get(1) }.toSet()
        val dismissing = activeSet(false)
        val restoring = activeSet(true)
        return fresh.copy(
            recitations = over(fresh.recitations, old.recitations) { it.id }.filterNot { it.id in deleted },
            rewards = over(fresh.rewards, old.rewards) { it.id }.filterNot { it.id in deleted },
            students = over(fresh.students, old.students) { it.id }.filterNot { it.id in deleted || it.id in dismissing },
            dismissed = fresh.dismissed.filterNot { it.id in restoring } + old.dismissed.filter { it.id in dismissing && fresh.dismissed.none { f -> f.id == it.id } },
            events = if (st.outbox.none { it.table == "admin_events" }) fresh.events else fresh.events.map { e -> old.events.firstOrNull { it.id == e.id }?.readAt?.let { r -> e.copy(readAt = r) } ?: e },
            circles = over(fresh.circles, old.circles) { it.id }.filterNot { it.id in deleted },
            announcements = over(fresh.announcements, old.announcements) { it.id }.filterNot { it.id in deleted },
            attendance = over(fresh.attendance, old.attendance) { SyncPolicy.attendanceKey(it.studentId, it.day) }
        )
    }

    /**
     * v3.2: send every waiting change, oldest first, each with its own owner's login.
     * Sent / already there -> done. No network or server busy -> stop, try later. Login ended -> keep, ask
     * for the password. Refused by the rules -> kept aside with the reason (never silently dropped).
     */
    private suspend fun flushOutbox() {
        val blocked = mutableSetOf<String>()
        while (true) {
            val op = st.outbox.firstOrNull { it.owner !in blocked && it.owner !in st.needsReauth } ?: break
            try {
                send(op); done(op)
            } catch (e: OfflineException) {
                throw e
            } catch (e: NeedsReauth) {
                blocked += e.owner; markReauth(e.owner)
            } catch (e: Superseded) {
                done(op, superseded = true)
            } catch (e: PermissionDenied) {
                blocked += op.owner; markReauth(op.owner)
            } catch (e: ApiException) {
                when (SyncPolicy.classify(e.code)) {
                    SyncPolicy.Outcome.SENT -> done(op)
                    SyncPolicy.Outcome.RETRY -> throw e
                    SyncPolicy.Outcome.REAUTH -> { blocked += op.owner; markReauth(op.owner) }
                    SyncPolicy.Outcome.REJECTED -> lock.withLock {
                        android.util.Log.i("ALSIDDIQ_SELFTEST", "sync: server refused ${op.table} (${e.code}) -> kept as rejected")
                        st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }, rejected = st.rejected + op.copy(reason = e.message)); save(); publish()
                    }
                }
            }
        }
        releaseIdleLogins()
    }

    private suspend fun done(op: PendingOp, superseded: Boolean = false) = lock.withLock {
        if (superseded) android.util.Log.i("ALSIDDIQ_SELFTEST", "sync: ${op.table} newer on the server -> last write wins, ours skipped")
        st = st.copy(outbox = st.outbox.filterNot { it.id == op.id }, superseded = st.superseded + if (superseded) 1 else 0); save(); publish()
        op.file?.let { runCatching { File(it).delete() } }
    }

    /** Logins kept only for waiting changes are ended once those are sent. */
    private suspend fun releaseIdleLogins() {
        val idle = st.held.filterKeys { id -> st.outbox.none { it.owner == id } }
        if (idle.isEmpty() && st.heldMembers.keys.none { id -> st.outbox.none { it.owner == id } }) return
        lock.withLock {
            st = st.copy(held = st.held - idle.keys,
                heldMembers = st.heldMembers.filterKeys { id -> st.outbox.any { it.owner == id } },
                needsReauth = st.needsReauth.filter { id -> st.outbox.any { it.owner == id } })
            save(); publish()
        }
        idle.values.forEach { s -> scope.launch { api.signOut(s.accessToken) } }
    }

    private suspend fun send(op: PendingOp) {
        val t = tokenFor(op.owner)
        when (op.type) {
            "upsert" -> {
                val body = lww(t, op) ?: throw Superseded()
                api.upsert(t, op.table, body, op.onConflict, op.ignoreDuplicates)
            }
            "patch" -> api.patch(t, op.table, op.filter, json.parseToJsonElement(op.body))
            "delete" -> api.delete(t, op.table, op.filter)
            "photo" -> {
                val o = json.parseToJsonElement(op.body).jsonObject
                val sid = o["student_id"]!!.jsonPrimitive.content
                val path = o["path"]?.jsonPrimitive?.contentOrNull
                val old = o["old"]?.jsonPrimitive?.contentOrNull
                if (path != null) {
                    val bytes = op.file?.let { f -> File(f).takeIf { it.exists() }?.readBytes() } ?: photoFile(path).takeIf { it.exists() }?.readBytes()
                    if (bytes != null) api.uploadObject(t, "student-photos", path, bytes)
                }
                api.patch(t, "students", "id=eq.$sid", buildJsonObject { put("photo_path", path); put("updated_at", Rows.iso(op.at)) })
                if (old != null && old != path) runCatching { api.deleteObject(t, "student-photos", old) }
            }
        }
    }

    /**
     * v3.2 last-write-wins by the time the change was made on the phone: a student edit or an attendance
     * mark is sent only if the server doesn't already hold a LATER change of the same row. Null = nothing left to send.
     */
    private suspend fun lww(t: String, op: PendingOp): JsonElement? {
        val body = json.parseToJsonElement(op.body)
        return when (op.table) {
            "students" -> {
                val id = body.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return body
                val server = runCatching { api.selectAll(t, "students", "select=updated_at&id=eq.$id").firstOrNull()?.get("updated_at")?.jsonPrimitive?.contentOrNull }.getOrNull()
                if (SyncPolicy.shouldSend(op.at, server?.let { Rows.ts(it) })) body else null
            }
            "attendance" -> {
                val rows = (body as? JsonArray) ?: return body
                val day = rows.firstOrNull()?.jsonObject?.get("day")?.jsonPrimitive?.contentOrNull ?: return body
                val ids = rows.mapNotNull { it.jsonObject["student_id"]?.jsonPrimitive?.contentOrNull }
                val server = runCatching {
                    api.selectAll(t, "attendance", "select=student_id,recorded_at&day=eq.$day&student_id=in.(${ids.joinToString(",")})")
                        .associate { it["student_id"]!!.jsonPrimitive.content to Rows.ts(it["recorded_at"]?.jsonPrimitive?.contentOrNull) }
                }.getOrDefault(emptyMap())
                val keep = rows.filter { r ->
                    val sid = r.jsonObject["student_id"]?.jsonPrimitive?.contentOrNull
                    SyncPolicy.shouldSend(op.at, server[sid]?.takeIf { it > 0 })
                }
                if (keep.isEmpty()) null else JsonArray(keep)
            }
            else -> body
        }
    }

    /**
     * Apply a change on screen, queue it (with its owner, time and rows), and try to send it now.
     * Online and refused by the rules -> the change is undone and the reason returned.
     * Offline, server busy, or the login needs the password again -> kept and sent automatically later.
     */
    private suspend fun <T> write(op0: PendingOp, value: T, local: (CenterSnapshot) -> CenterSnapshot): Result<T> {
        val op = op0.copy(owner = st.me?.id ?: op0.owner)
        val before = st.cache
        lock.withLock {
            st = st.copy(cache = local(st.cache), outbox = st.outbox + op); save(); publish()
        }
        try {
            syncLock.withLock { flushOutbox() }
        } catch (_: Exception) { /* offline / busy: stays queued */ }
        val refused = st.rejected.firstOrNull { it.id == op.id }
        if (refused != null) {
            // Refused right now, in front of the user: undo it on screen and say why (nothing waits silently).
            lock.withLock { st = st.copy(rejected = st.rejected.filterNot { it.id == op.id }, cache = if (st.outbox.isEmpty()) before else st.cache); save() }
            runCatching { syncNow() }
            publish()
            return Result.failure(ApiException(403, refused.reason ?: "رفض الخادم هذا التعديل"))
        }
        if (st.outbox.none { it.id == op.id }) scope.launch { runCatching { syncNow() } }
        else runCatching { com.alsiddiq.app.notify.SyncWorker.kick(context) }   // still waiting: WorkManager sends it when online
        return Result.success(value)
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

    override suspend fun saveTeacherName(id: String?, name: String): Result<TeacherName> = guarded {
        require(Permissions.canManageMembers(me()))
        if (name.isBlank()) throw ValidationError("اكتب اسم المعلم")
        val saved = TeacherName(id ?: newId(), name.trim(), true)
        write(PendingOp(type = "upsert", table = "teacher_names",
            body = buildJsonObject { put("id", saved.id); put("center_id", center()); put("name", saved.name); put("active", true) }.toString()), saved) { d ->
            d.copy(teacherNames = if (d.teacherNames.any { it.id == saved.id }) d.teacherNames.map { if (it.id == saved.id) saved else it } else d.teacherNames + saved)
        }
    }

    override suspend fun removeTeacherName(id: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        write(PendingOp(type = "delete", table = "teacher_names", filter = "id=eq.$id"), Unit) { d -> d.copy(teacherNames = d.teacherNames.filterNot { it.id == id }) }
    }

    // ---------------- v3.1: who is recording (optional name picked on this phone) ----------------
    private val prefs = context.getSharedPreferences("alsiddiq_prefs", Context.MODE_PRIVATE)
    private val _recorder = MutableStateFlow(prefs.getString("recorder_name", null))
    override val recorderName: StateFlow<String?> = _recorder.asStateFlow()
    override fun setRecorderName(name: String?) {
        val n = name?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString("recorder_name", n).apply(); _recorder.value = n
    }
    /** v3.3: a teacher records under his own account, so his own name goes on the record. */
    private fun byName(): String? = st.me?.takeIf { it.role == Role.TEACHER && !it.allCircles }?.fullName

    // ---------------- v3.1: student photos (private bucket) ----------------
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    private fun photoFile(path: String) = File(photoDir, path.replace('/', '_'))

    override suspend fun setStudentPhoto(studentId: String, jpeg: ByteArray?): Result<Unit> = guarded {
        val u = me(); val d = st.cache
        val s = d.student(studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canEditStudent(u, s, d))
        val old = s.photoPath
        val path = if (jpeg != null) "${center()}/$studentId/${UUID.randomUUID().toString().take(8)}.jpg" else null
        // v3.2: the photo is kept on the phone and shown at once; it uploads with the other waiting changes.
        val local = if (jpeg != null && path != null) photoFile(path).also { it.writeBytes(jpeg) } else null
        val body = buildJsonObject { put("student_id", studentId); put("path", path); put("old", old) }
        write(PendingOp(type = "photo", table = "student-photos", body = body.toString(), keys = listOf(studentId, "photo:$studentId"),
            label = "صورة ${s.fullName}", file = null), Unit) { snap ->
            snap.copy(students = snap.students.map { if (it.id == studentId) it.copy(photoPath = path) else it })
        }.also { r -> if (r.isFailure) local?.delete() }
    }

    override suspend fun studentPhoto(path: String): ByteArray? {
        val f = photoFile(path)
        if (f.exists() && f.length() > 0) return runCatching { f.readBytes() }.getOrNull()
        val bytes = runCatching { api.downloadObject(token(), "student-photos", path) }.getOrNull() ?: return null
        runCatching { f.writeBytes(bytes) }
        return bytes
    }


    // ---------------- v3.8: teachers' attendance, budget, subscriptions sheet, circle photo (online) ----------------
    private suspend fun <T> net(what: String, block: suspend () -> T): T =
        try { block() } catch (e: OfflineException) { throw ValidationError("$what يحتاج اتصالاً بالإنترنت") }
    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.num(k: String): Double? = this[k]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    override suspend fun teacherMarks(fromDay: String, toDay: String): Result<List<TeacherMark>> = guarded {
        require(me().role == Role.ADMIN)
        val rows = net("حضور المعلمين") { api.selectAll(token(), "teacher_attendance", "select=teacher_profile_id,day,status&day=gte.$fromDay&day=lte.$toDay&order=day.desc") }
        Result.success(rows.mapNotNull { o -> val t = o.str("teacher_profile_id") ?: return@mapNotNull null
            TeacherMark(t, o.str("day") ?: return@mapNotNull null, o.str("status") == "present") })
    }

    override suspend fun saveTeacherMarks(day: String, present: Map<String, Boolean>): Result<Int> = guarded {
        val u = me(); require(u.role == Role.ADMIN)
        if (present.isEmpty()) throw ValidationError("حدّد حاضر أو غائب لمعلم واحد على الأقل")
        val rows = JsonArray(present.map { (tid, p) -> buildJsonObject {
            put("center_id", center()); put("teacher_profile_id", tid); put("day", day); put("status", if (p) "present" else "absent"); put("marked_by", u.id)
        } })
        net("حفظ حضور المعلمين") { api.upsert(token(), "teacher_attendance", rows, "teacher_profile_id,day") }
        Result.success(present.size)
    }

    override suspend fun budgetEntries(): Result<List<BudgetEntry>> = guarded {
        require(me().role == Role.ADMIN)
        val rows = net("الميزانية") { api.selectAll(token(), "budget_entries", "select=*&order=day.desc,created_at.desc") }
        Result.success(rows.mapNotNull { o -> BudgetEntry(o.str("id") ?: return@mapNotNull null, o.str("kind") ?: "income", o.num("amount") ?: 0.0,
            o.str("day") ?: "", o.str("student_id"), o.str("note") ?: "", Rows.ts(o.str("created_at"))) })
    }

    override suspend fun saveBudgetEntry(e: BudgetEntry, isNew: Boolean): Result<Unit> = guarded {
        val u = me(); require(u.role == Role.ADMIN)
        if (e.amount <= 0) throw ValidationError("اكتب مبلغاً أكبر من صفر")
        if (!e.isIncome && e.note.isBlank()) throw ValidationError("اكتب ملاحظة الصرف")
        val body = buildJsonObject {
            put("kind", e.kind); put("amount", e.amount); put("day", e.day); put("student_id", e.studentId); put("note", e.note.trim().ifBlank { null })
        }
        net("حفظ الميزانية") {
            if (isNew) api.insert(token(), "budget_entries", buildJsonObject { put("id", e.id); put("center_id", center()); put("created_by", u.id); body.forEach { (k, v) -> put(k, v) } })
            else api.patch(token(), "budget_entries", "id=eq.${e.id}", body)
        }
        Result.success(Unit)
    }

    override suspend fun deleteBudgetEntry(id: String): Result<Unit> = guarded {
        require(me().role == Role.ADMIN)
        net("حذف القيد") { api.delete(token(), "budget_entries", "id=eq.$id") }
        Result.success(Unit)
    }

    override suspend fun subscriptions(month: String): Result<List<SubscriptionMark>> = guarded {
        val u = me(); require(u.role == Role.ADMIN || (u.role == Role.TEACHER && !u.allCircles))
        val rows = net("كشف الاشتراكات") { api.selectAll(token(), "student_subscriptions", "select=student_id,month,paid,amount&month=eq.$month") }
        Result.success(rows.mapNotNull { o -> SubscriptionMark(o.str("student_id") ?: return@mapNotNull null, o.str("month") ?: month, o.str("paid") == "true", o.num("amount")) })
    }

    override suspend fun saveSubscription(m: SubscriptionMark): Result<Unit> = guarded {
        val u = me(); val d = st.cache
        val s = d.student(m.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(u.role == Role.ADMIN || (u.role == Role.TEACHER && d.circle(s.circleId)?.taughtBy(u.id) == true))
        if (m.paid && (m.amount ?: 0.0) < 0) throw ValidationError("المبلغ غير صحيح")
        val row = buildJsonObject {
            put("center_id", center()); put("student_id", m.studentId); put("month", m.month); put("paid", m.paid)
            put("amount", if (m.paid) m.amount else null); put("marked_by", u.id); put("updated_at", Rows.iso(System.currentTimeMillis()))
        }
        net("حفظ الاشتراك") { api.upsert(token(), "student_subscriptions", JsonArray(listOf(row)), "student_id,month") }
        Result.success(Unit)
    }

    override suspend fun setCirclePhoto(circleId: String, jpeg: ByteArray?): Result<Unit> = guarded {
        val u = me(); val c = st.cache.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        require(u.role == Role.ADMIN || (u.role == Role.TEACHER && !u.allCircles && c.taughtBy(u.id)))
        val old = c.photoPath
        val path = jpeg?.let { "${center()}/circles/$circleId/${UUID.randomUUID().toString().take(8)}.jpg" }
        net("صورة الحلقة") {
            if (jpeg != null && path != null) { api.uploadObject(token(), "student-photos", path, jpeg); photoFile(path).writeBytes(jpeg) }
            api.rpc(token(), "set_circle_photo", buildJsonObject { put("cid", circleId); put("path", path) })
        }
        if (old != null && old != path) runCatching { api.deleteObject(token(), "student-photos", old); photoFile(old).delete() }
        lock.withLock { st = st.copy(cache = st.cache.copy(circles = st.cache.circles.map { if (it.id == circleId) it.copy(photoPath = path) else it })); save() }
        publish()
        Result.success(Unit)
    }

    override suspend fun circlePhoto(path: String): ByteArray? = studentPhoto(path)

    override suspend fun setCircleTeachers(circleId: String, teacherIds: List<String>): Result<Unit> = guarded {
        require(me().role == Role.ADMIN)
        val d = st.cache
        d.circle(circleId) ?: throw ValidationError("الحلقة غير موجودة")
        val ids = teacherIds.distinct()
        val first = ids.firstOrNull()
        runCatching { syncNow() }   // a just-created circle must reach the server before its teacher links
        net("تعديل معلمي الحلقة") {
            val t = token()
            // the first teacher stays the circle's single teacher (older builds and the reports read it)
            api.patch(t, "circles", "id=eq.$circleId", buildJsonObject {
                put("teacher_id", first); put("teacher_name", d.member(first)?.fullName); put("updated_at", Rows.iso(System.currentTimeMillis()))
            })
            api.delete(t, "circle_teachers", "circle_id=eq.$circleId" + if (ids.isEmpty()) "" else "&teacher_profile_id=not.in.(${ids.joinToString(",")})")
            if (ids.isNotEmpty()) api.upsert(t, "circle_teachers", JsonArray(ids.map { id -> buildJsonObject { put("circle_id", circleId); put("teacher_profile_id", id) } }),
                "circle_id,teacher_profile_id", ignore = true)
        }
        lock.withLock {
            st = st.copy(cache = st.cache.copy(circles = st.cache.circles.map {
                if (it.id == circleId) it.copy(teacherId = first, teacherName = d.member(first)?.fullName, teacherIds = ids) else it }))
            save()
        }
        publish()
        runCatching { syncNow() }
        Result.success(Unit)
    }

    override suspend fun setAccessPassword(target: Role, password: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        val pw = normPw(password)
        if (pw.length < 6) throw ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل")
        // v3.3: there is no shared teachers' password any more; teachers' passwords are set per teacher.
        if (target == Role.TEACHER) throw ValidationError("كلمة سر كل معلم من صفحة المعلم في «المعلمون»")
        adminFunction(buildJsonObject { put("action", "set_password"); put("target", if (target == Role.ADMIN) "admin" else "viewer"); put("password", pw) })
        Result.success(Unit)
    }

    // ---------------- v3.3: per-teacher accounts (admin only, online) ----------------
    override suspend fun saveTeacher(id: String?, name: String, circleId: String?, password: String?): Result<Member> = guarded {
        require(Permissions.canManageMembers(me()))
        if (name.isBlank()) throw ValidationError("اكتب اسم المعلم")
        val pw = password?.let { normPw(it) }?.takeIf { it.isNotEmpty() }
        if (id == null && pw == null) throw ValidationError("اختر كلمة سر للمعلم")
        if (pw != null && pw.length < 6) throw ValidationError("كلمة السر 6 أحرف أو أرقام على الأقل")
        val existing = st.cache.member(id)
        if (existing != null && !existing.active && pw == null) throw ValidationError("المعلم موقوف: اختر له كلمة سر جديدة لتشغيله")
        val o = adminFunction(buildJsonObject {
            put("action", "teacher_save"); if (id != null) put("id", id); put("name", name.trim())
            put("circle_id", circleId ?: ""); if (pw != null) put("password", pw)
        })
        val saved = memberFrom(o["profile"]!!.jsonObject).copy(active = true)
        lock.withLock {
            val d = st.cache
            val members = if (d.members.any { it.id == saved.id }) d.members.map { if (it.id == saved.id) saved else it } else d.members + saved
            val circles = d.circles.map { c -> when {
                c.id == circleId -> c.copy(teacherId = saved.id, teacherName = saved.fullName)
                c.teacherId == saved.id -> c.copy(teacherId = null, teacherName = null)
                else -> c } }
            st = st.copy(cache = d.copy(members = members, circles = circles)); save(); publish()
        }
        runCatching { syncNow() }
        Result.success(saved)
    }

    override suspend fun removeTeacher(id: String): Result<Unit> = guarded {
        require(Permissions.canManageMembers(me()))
        adminFunction(buildJsonObject { put("action", "teacher_remove"); put("id", id) })
        lock.withLock {
            val d = st.cache
            st = st.copy(cache = d.copy(members = d.members.map { if (it.id == id) it.copy(active = false) else it },
                circles = d.circles.map { if (it.teacherId == id) it.copy(teacherId = null, teacherName = null) else it })); save(); publish()
        }
        runCatching { syncNow() }
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
        write(PendingOp(type = "upsert", table = "circles", body = Rows.circleRow(saved, center()).toString(), keys = listOf(saved.id), label = "حلقة: ${saved.name}"), saved) { d ->
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
        write(PendingOp(type = "upsert", table = "students", body = Rows.studentRow(saved, center()).toString(), keys = listOf(saved.id), label = "الطالب ${saved.fullName}"), saved) { d ->
            d.copy(students = if (d.students.any { it.id == saved.id }) d.students.map { if (it.id == saved.id) saved else it } else d.students + saved)
        }
    }

    override suspend fun dismissStudent(id: String): Result<Unit> = guarded {
        val s = st.cache.students.firstOrNull { it.id == id } ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canDismissStudent(me(), s, st.cache))
        val now = System.currentTimeMillis()
        val body = buildJsonObject { put("active", false); put("dismissed_at", Rows.iso(now)); put("dismissed_by", me().id); put("updated_at", Rows.iso(now)) }
        val gone = s.copy(active = false, dismissedAt = now, dismissedBy = me().id)
        write(PendingOp(type = "patch", table = "students", filter = "id=eq.$id", body = body.toString(), keys = listOf(id), label = "فصل ${s.fullName}"), Unit) { d ->
            d.copy(students = d.students.filterNot { it.id == id }, dismissed = d.dismissed.filterNot { it.id == id } + gone)
        }
    }

    override suspend fun publishHonorCard(students: List<HonorStudent>, message: String): Result<Unit> = guarded {
        require(me().role == Role.ADMIN)
        if (students.isEmpty()) throw ValidationError("اختر طالباً واحداً على الأقل")
        try { api.insert(token(), "honor_cards", Rows.honorCardRow(students, message.trim(), center(), me().id)) }
        catch (e: OfflineException) { throw ValidationError("نشر التهنئة يحتاج اتصالاً بالإنترنت") }
        runCatching { syncNow() }
        Result.success(Unit)
    }

    override suspend fun markEventsRead(id: String?): Result<Unit> = guarded {
        require(me().role == Role.ADMIN)
        val now = System.currentTimeMillis()
        val filter = if (id == null) "read_at=is.null" else "id=eq.$id"
        write(PendingOp(type = "patch", table = "admin_events", filter = filter, body = buildJsonObject { put("read_at", Rows.iso(now)) }.toString(), label = "تنبيهات المعلمين"), Unit) { d ->
            d.copy(events = d.events.map { if ((id == null || it.id == id) && it.readAt == null) it.copy(readAt = now) else it })
        }
    }

    /** v3.6: teachers' new actions since the last check, for a local notification when the app syncs (not a push). */
    suspend fun fetchNewAdminEvents(): List<AdminEvent> {
        val u = st.me ?: return emptyList()
        if (u.role != Role.ADMIN) return emptyList()
        val rows = runCatching { api.selectAll(token(), "admin_events", "select=*&read_at=is.null&order=created_at.desc").map { Rows.event(it) } }.getOrDefault(emptyList())
        val seen = st.seenNotificationIds.toSet()
        val fresh = rows.filter { "ev:" + it.id !in seen }
        lock.withLock { st = st.copy(seenNotificationIds = (rows.map { "ev:" + it.id } + st.seenNotificationIds).distinct().take(500)); save() }
        return fresh
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
        val saved = r.copy(id = r.id.ifBlank { newId() }, teacherId = u.id, circleId = student.circleId, byName = r.byName ?: byName())
        write(PendingOp(type = "upsert", table = "recitations", ignoreDuplicates = true, body = Rows.recitationRow(saved, center()).toString(), keys = listOf(saved.id, saved.studentId), label = "تسميع ${student.fullName}"), saved) {
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
                put("by_name", byName())
            } }
        }
        write(PendingOp(type = "upsert", table = "attendance", onConflict = "student_id,day", body = rows.toString(), keys = marks.keys.map { SyncPolicy.attendanceKey(it, day) } + "att:$circleId:$day", label = "حضور ${circle.name} $day"), marks.size) { snap ->
            val kept = snap.attendance.filterNot { it.day == day && it.studentId in marks.keys }
            snap.copy(attendance = kept + marks.map { (sid, s) -> Attendance(newId(), sid, circleId, day, s, u.id, byName = byName()) })
        }
    }

    override suspend fun giveReward(r: Reward): Result<Reward> = guarded {
        val u = me(); val d = st.cache
        val student = d.student(r.studentId) ?: throw ValidationError("الطالب غير موجود")
        require(Permissions.canRecordFor(u, student, d))
        if (r.points == 0) throw ValidationError("حدّد عدد النقاط")
        val saved = r.copy(id = r.id.ifBlank { newId() }, awardedBy = u.id, byName = r.byName ?: byName())
        write(PendingOp(type = "upsert", table = "rewards", ignoreDuplicates = true, body = Rows.rewardRow(saved, center()).toString(), keys = listOf(saved.id), label = "مكافأة ${student.fullName}"), saved) {
            it.copy(rewards = listOf(saved) + it.rewards)
        }
    }

    // ---------------- communication ----------------
    override suspend fun postAnnouncement(a: Announcement): Result<Announcement> = guarded {
        val u = me()
        require(Permissions.canPostAnnouncement(u))
        if (a.title.isBlank()) throw ValidationError("اكتب عنوان الإعلان")
        val saved = a.copy(id = a.id.ifBlank { newId() }, createdBy = u.id, byName = a.byName ?: byName())
        write(PendingOp(type = "upsert", table = "announcements", ignoreDuplicates = true, body = Rows.announcementRow(saved, center()).toString(), keys = listOf(saved.id), label = "إعلان: ${saved.title}"), saved) {
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

    // ---------------- v3.4 «عن المركز» (public read with the public key; admin writes, online) ----------------
    private val aboutLocal = AboutLocal(context)
    override val about: StateFlow<AboutPage> = aboutLocal.flow.asStateFlow()

    override suspend fun refreshAbout() {
        runCatching {
            val row = api.selectPublic("centre_about", "select=*&limit=1").firstOrNull()
            val btns = runCatching { api.selectPublic("centre_about_buttons", "select=*&order=sort") }.getOrDefault(emptyList())
            val photos = row?.get("photos")?.let { el -> runCatching { el.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull() }
            aboutLocal.set(AboutPage(
                body = row?.get("body")?.jsonPrimitive?.contentOrNull,
                photos = if (row == null) listOf(AboutPage.BUNDLED_PHOTO) else photos ?: emptyList(),
                buttons = AboutLocal.sorted(btns.map { b -> AboutButton(b["id"]!!.jsonPrimitive.content, b["label"]?.jsonPrimitive?.contentOrNull ?: "",
                    b["kind"]?.jsonPrimitive?.contentOrNull ?: "link", b["value"]?.jsonPrimitive?.contentOrNull ?: "", b["sort"]?.jsonPrimitive?.intOrNull ?: 0) }),
                updatedAt = Rows.ts(row?.get("updated_at")?.jsonPrimitive?.contentOrNull), fromServer = true))
        }.onFailure { android.util.Log.i("ALSIDDIQ_SELFTEST", "about: offline/unavailable -> showing the saved copy (${it.message})") }
    }

    override suspend fun aboutPhoto(path: String): ByteArray? {
        aboutLocal.cached(path)?.let { return it }
        val bytes = runCatching { api.downloadPublic("center-about", path) }.getOrNull() ?: return null
        aboutLocal.cache(path, bytes); return bytes
    }

    private suspend fun adminToken(): String {
        require(Permissions.canManageCenter(me()))
        return try { token() } catch (e: OfflineException) { throw ValidationError("تعديل «عن المركز» يحتاج اتصالاً بالإنترنت") }
    }
    private suspend fun <T> online(block: suspend () -> T): T = try { block() } catch (e: OfflineException) { throw ValidationError("تعديل «عن المركز» يحتاج اتصالاً بالإنترنت") }

    private suspend fun putAbout(t: String, body: String, photos: List<String>) = online {
        val now = System.currentTimeMillis()
        api.upsert(t, "centre_about", buildJsonObject {
            put("center_id", center()); put("body", body); put("photos", JsonArray(photos.map { JsonPrimitive(it) })); put("updated_at", Rows.iso(now))
        }, "center_id")
        aboutLocal.set(aboutLocal.page.copy(body = body, photos = photos, updatedAt = now))
    }

    override suspend fun saveAboutText(body: String): Result<Unit> = guarded {
        val t = adminToken()
        putAbout(t, body.trim(), aboutLocal.page.photos); Result.success(Unit)
    }

    override suspend fun addAboutPhoto(jpeg: ByteArray): Result<Unit> = guarded {
        val t = adminToken()
        val path = "${center()}/${UUID.randomUUID().toString().take(8)}.jpg"
        online { api.uploadObject(t, "center-about", path, jpeg) }
        aboutLocal.cache(path, jpeg)
        putAbout(t, aboutLocal.currentText(), aboutLocal.page.photos + path); Result.success(Unit)
    }

    override suspend fun removeAboutPhoto(path: String): Result<Unit> = guarded {
        val t = adminToken()
        putAbout(t, aboutLocal.currentText(), aboutLocal.page.photos - path)
        if (!path.startsWith("bundled:")) { runCatching { api.deleteObject(t, "center-about", path) }; aboutLocal.forget(path) }
        Result.success(Unit)
    }

    override suspend fun saveAboutButton(b: AboutButton): Result<Unit> = guarded {
        val t = adminToken()
        if (b.label.isBlank() || b.value.isBlank()) throw ValidationError("اكتب اسم الزر وقيمته")
        val list = aboutLocal.page.buttons
        val saved = if (b.id.isBlank()) b.copy(id = newId(), sort = (list.maxOfOrNull { it.sort } ?: -1) + 1) else b
        online { api.upsert(t, "centre_about_buttons", buildJsonObject {
            put("id", saved.id); put("center_id", center()); put("label", saved.label.trim()); put("kind", saved.kind); put("value", saved.value.trim()); put("sort", saved.sort)
        }, "id") }
        aboutLocal.set(aboutLocal.page.copy(buttons = AboutLocal.sorted(list.filterNot { it.id == saved.id } + saved)))
        Result.success(Unit)
    }

    override suspend fun deleteAboutButton(id: String): Result<Unit> = guarded {
        val t = adminToken()
        online { api.delete(t, "centre_about_buttons", "id=eq.$id") }
        aboutLocal.set(aboutLocal.page.copy(buttons = aboutLocal.page.buttons.filterNot { it.id == id }))
        Result.success(Unit)
    }

    override suspend fun moveAboutButton(id: String, up: Boolean): Result<Unit> = guarded {
        val t = adminToken()
        val moved = AboutLocal.move(aboutLocal.page.buttons, id, up)
        online { moved.forEach { b -> if (aboutLocal.page.buttons.firstOrNull { it.id == b.id }?.sort != b.sort)
            api.patch(t, "centre_about_buttons", "id=eq.${b.id}", buildJsonObject { put("sort", b.sort) }) } }
        aboutLocal.set(aboutLocal.page.copy(buttons = moved))
        Result.success(Unit)
    }

    fun isSignedIn() = st.session != null
}
