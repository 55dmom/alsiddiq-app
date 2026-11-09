package com.alsiddiq.app

import com.alsiddiq.app.domain.RestDay
import com.alsiddiq.app.domain.TeacherStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3.9: Friday is a rest day; the records run Saturday to Thursday. */
class RestDayTest {
    private val friday = LocalDate.of(2026, 11, 13)      // a Friday
    private val thursday = LocalDate.of(2026, 11, 12)
    private val saturday = LocalDate.of(2026, 11, 14)

    @Test fun fridayIsRestOthersAreNot() {
        assertTrue(RestDay.isRest(friday))
        assertFalse(RestDay.isRest(thursday))
        assertFalse(RestDay.isRest(saturday))
    }

    @Test fun navigationSkipsFriday() {
        assertEquals(thursday, RestDay.prevWorkDay(saturday))
        assertEquals(saturday, RestDay.nextWorkDay(thursday, saturday))
        assertEquals(thursday, RestDay.lastWorkDayOnOrBefore(friday))
        assertNull(RestDay.nextWorkDay(thursday, thursday))   // nothing after today yet
    }

    @Test fun teacherStatusWordsMatchTheServer() {
        assertEquals(listOf("present", "absent", "excused"), TeacherStatus.ALL)
        assertEquals("مستأذن", TeacherStatus.label(TeacherStatus.EXCUSED))
    }
}
