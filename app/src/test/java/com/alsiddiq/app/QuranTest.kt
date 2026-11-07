package com.alsiddiq.app

import com.alsiddiq.app.core.Quran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTest {
    @Test fun totals() {
        assertEquals(114, Quran.names.size)
        assertEquals(6236, Quran.ayahCounts.sum())
        assertEquals(6236, Quran.ayatBetween(1, 1, 114, 6))
    }

    @Test fun pagesAndJuz() {
        assertEquals(1, Quran.pageOf(1, 1))
        assertEquals(2, Quran.pageOf(2, 1))
        assertEquals(604, Quran.pageOf(114, 6))
        assertEquals(2, Quran.juzOf(2, 142))
        assertEquals(30, Quran.juzOf(78, 1))
        assertEquals(604.0, Quran.pagesOf(1, 6236), 0.001)
    }

    @Test fun ranges() {
        assertEquals(5, Quran.ayatBetween(2, 1, 2, 5))
        assertEquals(0, Quran.ayatBetween(2, 5, 2, 1))
        assertEquals(0, Quran.ayatBetween(1, 8, 1, 9))
        assertTrue(Quran.isValid(2, 286))
        assertTrue(!Quran.isValid(2, 287))
        // Al-Fatiha (7) + Al-Baqarah 1..5
        assertEquals(12, Quran.ayatBetween(1, 1, 2, 5))
    }
}
