package com.alsiddiq.app

import com.alsiddiq.app.domain.SyncPolicy
import com.alsiddiq.app.domain.SyncPolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test fun classify() {
        assertEquals(Outcome.SENT, SyncPolicy.classify(201))
        assertEquals(Outcome.SENT, SyncPolicy.classify(409))
        assertEquals(Outcome.REAUTH, SyncPolicy.classify(401))
        assertEquals(Outcome.REJECTED, SyncPolicy.classify(403))
        assertEquals(Outcome.REJECTED, SyncPolicy.classify(400))
        assertEquals(Outcome.RETRY, SyncPolicy.classify(429))
        assertEquals(Outcome.RETRY, SyncPolicy.classify(500))
        assertEquals(Outcome.RETRY, SyncPolicy.classify(503))
    }

    @Test fun lastWriteWins() {
        assertTrue(SyncPolicy.shouldSend(1000, null))
        assertTrue(SyncPolicy.shouldSend(1000, 999))
        assertTrue(SyncPolicy.shouldSend(1000, 1000))
        assertFalse(SyncPolicy.shouldSend(1000, 1001))
    }

    @Test fun statusLine() {
        assertEquals("كل شي مرفوع ✅", SyncPolicy.statusLine(0, false, 0))
        assertEquals("٣ بانتظار الإرسال", SyncPolicy.statusLine(3, false, 0))
        assertEquals("١٢ بانتظار الإرسال • أدخل كلمة السر لرفعها", SyncPolicy.statusLine(12, true, 0))
    }
}
