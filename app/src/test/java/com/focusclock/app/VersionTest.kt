package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun aTagWithAVReadsTheSameAsTheNumberAlone() {
        assertEquals(0, Version.compare("v0.4.0", "0.4.0"))
        assertEquals(listOf(0, 4, 0), Version.parts("v0.4.0"))
    }

    @Test
    fun newerVersionsCompareHigher() {
        assertTrue(Version.compare("0.4.1", "0.4.0") > 0)
        assertTrue(Version.compare("0.5.0", "0.4.9") > 0)
        assertTrue(Version.compare("1.0.0", "0.99.99") > 0)
        assertTrue(Version.compare("0.3.0", "0.4.0") < 0)
    }

    @Test
    fun partsAreComparedAsNumbersNotText() {
        assertTrue(Version.compare("0.10.0", "0.9.0") > 0)
    }

    @Test
    fun missingPartsCountAsZero() {
        assertEquals(0, Version.compare("0.4", "0.4.0"))
        assertTrue(Version.compare("0.4.1", "0.4") > 0)
    }

    @Test
    fun suffixesAndJunkDoNotBreakIt() {
        assertEquals(0, Version.compare("v0.4.0-beta", "0.4.0"))
        assertEquals(listOf(0), Version.parts("latest"))
    }
}
