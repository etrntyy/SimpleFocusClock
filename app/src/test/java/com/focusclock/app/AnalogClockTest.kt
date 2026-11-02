package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogClockTest {
    @Test
    fun theHandsSitWhereTheyShouldAtTenFortyTwo() {
        val t = FaceTime.of(10, 42, 0)
        assertEquals(252f, t.minuteDeg, 1e-3f) // 42 minutes is 252 degrees
        assertEquals(321f, t.hourDeg, 1e-3f) // ten hours and 42 minutes is 10.7 of 12
        assertEquals(0.7f, t.minuteFraction, 1e-3f)
        assertEquals(10.7f, t.hour12, 1e-3f)
        assertEquals(42, t.minute)
    }

    @Test
    fun midnightAndNoonBothSitAtTwelve() {
        assertEquals(0f, FaceTime.of(0, 0, 0).hourDeg, 1e-3f)
        assertEquals(0f, FaceTime.of(12, 0, 0).hourDeg, 1e-3f)
        assertEquals(0f, FaceTime.of(12, 0, 0).hourFraction, 1e-3f)
    }

    @Test
    fun secondsNudgeTheMinuteHand() {
        val t = FaceTime.of(3, 30, 30)
        assertEquals(183f, t.minuteDeg, 1e-3f)
        assertEquals(180f, t.secondDeg, 1e-3f)
        assertEquals(0.5f, t.secondFraction, 1e-3f)
    }

    @Test
    fun everyDrawnFaceHasItsOwnName() {
        val names = AnalogStyle.entries.map { it.label }
        assertEquals(15, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue("Words" !in names)
        assertTrue("Disc" in names)
    }
}
