package com.alsiddiq.app.domain

/**
 * v3.2 offline sync: the pure decisions behind the outbox (unit-tested, no Android).
 *  - Nothing a teacher or the admin saved is ever dropped: a write either reaches the server,
 *    waits (no network / server busy / login expired), or is kept as «رفضه الخادم» with the reason.
 *  - Conflicts: last write wins, by the time the change was made on the phone.
 */
object SyncPolicy {
    enum class Outcome { SENT, RETRY, REAUTH, REJECTED }

    /** What an HTTP answer from the server means for one queued write. */
    fun classify(code: Int): Outcome = when {
        code in 200..299 -> Outcome.SENT
        code == 409 -> Outcome.SENT          // already there (same client id): sending twice is harmless
        code == 401 -> Outcome.REAUTH        // the login ended: ask for the password, keep the write
        code == 408 || code == 425 || code == 429 -> Outcome.RETRY
        code in 400..499 -> Outcome.REJECTED // rules/data: kept aside with the reason, never silently lost
        else -> Outcome.RETRY                // 5xx and anything odd: try again later
    }

    /** Last-write-wins: send ours unless the server already holds a change made AFTER ours. */
    fun shouldSend(localChangedAt: Long, serverChangedAt: Long?): Boolean =
        serverChangedAt == null || serverChangedAt <= localChangedAt

    /** The status line under the header. */
    fun statusLine(pending: Int, needsPassword: Boolean, rejected: Int): String = when {
        needsPassword -> "${ar(pending)} بانتظار الإرسال • أدخل كلمة السر لرفعها"
        pending > 0 -> "${ar(pending)} بانتظار الإرسال"
        rejected > 0 -> "كل شي مرفوع ✅ • ${ar(rejected)} رفضها الخادم"
        else -> "كل شي مرفوع ✅"
    }

    fun ar(n: Int): String = n.toString().map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("")

    /** UI keys of the rows a queued write touches («بانتظار الإرسال» chips). */
    fun attendanceKey(studentId: String, day: String) = "att:$studentId:$day"
}
