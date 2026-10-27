package com.focusclock.app

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test
import org.junit.Assert.assertTrue

class NoiseGeneratorTest {
    private fun samples(kind: NoiseKind, n: Int = 44_100) =
        NoiseGenerator(kind, 44_100, Random(1)).let { g -> FloatArray(n) { g.next() } }

    /** Mean absolute step between neighbouring samples relative to the level: higher means more treble. */
    private fun brightness(x: FloatArray): Double {
        var d = 0.0; var a = 0.0
        for (i in 1 until x.size) { d += abs(x[i] - x[i - 1]); a += abs(x[i]) }
        return d / a
    }

    @Test fun everyKindStaysAudibleAndNearlyInRange() {
        for (k in NoiseKind.entries) {
            val x = samples(k, 44_100 * 20)
            val level = x.map { abs(it).toDouble() }.average()
            val clipped = x.count { abs(it) > 1f } / x.size.toDouble()
            assertTrue("$k level $level", level in 0.05..0.6)
            assertTrue("$k clipped $clipped", clipped < 0.01)
        }
    }

    @Test fun brightnessFollowsTheColours() {
        val b = NoiseKind.entries.associateWith { brightness(samples(it)) }
        assertTrue(b.getValue(NoiseKind.Brown) < b.getValue(NoiseKind.Pink))
        assertTrue(b.getValue(NoiseKind.Pink) < b.getValue(NoiseKind.White))
        assertTrue(b.getValue(NoiseKind.White) < b.getValue(NoiseKind.Blue))
        assertTrue(b.getValue(NoiseKind.Blue) < b.getValue(NoiseKind.Violet) * 1.2)
    }
}
