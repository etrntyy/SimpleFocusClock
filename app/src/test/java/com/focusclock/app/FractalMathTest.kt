package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class FractalMathTest {
    @Test
    fun theHilbertCurveVisitsEveryCellOnceAndMovesOneCellAtATime() {
        for (order in 1..5) {
            val side = 1 shl order
            val p = FractalMath.hilbert(order)
            assertEquals(side * side * 2, p.size)
            val cells = (0 until side * side).map { Pair((p[2 * it] * side).toInt(), (p[2 * it + 1] * side).toInt()) }
            assertEquals(side * side, cells.toSet().size)
            cells.zipWithNext().forEach { (a, b) -> assertEquals(1, abs(a.first - b.first) + abs(a.second - b.second)) }
        }
    }

    @Test
    fun theHilbertCurveStartsAndEndsOnTheSameEdge() {
        val side = 1 shl 4
        val p = FractalMath.hilbert(4)
        assertEquals(0, (p[0] * side).toInt())
        assertEquals(0, (p[1] * side).toInt())
        val last = side * side - 1
        assertEquals(side - 1, (p[2 * last] * side).toInt())
        assertEquals(0, (p[2 * last + 1] * side).toInt())
    }

    @Test
    fun theDragonCurveHasTheRightNumberOfTurnsAndAlternatesAtTheStart() {
        assertEquals(0, FractalMath.dragonTurns(0).size)
        assertEquals(2047, FractalMath.dragonTurns(11).size)
        assertEquals(listOf(1, 1, -1), FractalMath.dragonTurns(2).toList())
    }

    @Test
    fun everyDragonSegmentIsOneLong() {
        val turns = FractalMath.dragonTurns(8)
        for (angle in listOf(Math.PI / 2, 1.2, 2.0)) {
            val p = FractalMath.dragon(turns, angle)
            for (i in 0 until turns.size + 1) {
                val dx = p[2 * i + 2] - p[2 * i]
                val dy = p[2 * i + 3] - p[2 * i + 1]
                assertEquals(1.0, Math.hypot(dx.toDouble(), dy.toDouble()), 1e-3)
            }
        }
    }
}
