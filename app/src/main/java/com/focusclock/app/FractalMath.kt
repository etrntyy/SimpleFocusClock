package com.focusclock.app

import kotlin.math.cos
import kotlin.math.sin

/** The numbers behind the fractal animations. Nothing here touches the screen, so it can be tested on its own. */
object FractalMath {
    /**
     * The Hilbert curve of [order], as x then y for each of its 4^order points, scaled into the unit square. Neighbouring
     * points are always one cell apart, so joining them in order draws one unbroken line that fills the square.
     */
    fun hilbert(order: Int): FloatArray {
        val side = 1 shl order
        val out = FloatArray(side * side * 2)
        for (d in 0 until side * side) {
            var t = d
            var x = 0
            var y = 0
            var s = 1
            while (s < side) {
                val rx = 1 and (t / 2)
                val ry = 1 and (t xor rx)
                if (ry == 0) {
                    if (rx == 1) {
                        x = s - 1 - x
                        y = s - 1 - y
                    }
                    val swap = x
                    x = y
                    y = swap
                }
                x += s * rx
                y += s * ry
                t /= 4
                s *= 2
            }
            out[2 * d] = (x + 0.5f) / side
            out[2 * d + 1] = (y + 0.5f) / side
        }
        return out
    }

    /** The left and right turns (1 or -1) that fold a strip of paper [order] times. */
    fun dragonTurns(order: Int): IntArray {
        var turns = IntArray(0)
        repeat(order) {
            val next = IntArray(turns.size * 2 + 1)
            turns.copyInto(next)
            next[turns.size] = 1
            for (i in turns.indices) next[turns.size + 1 + i] = -turns[turns.size - 1 - i]
            turns = next
        }
        return turns
    }

    /** The dragon curve for [turns], each one turning by [angle] radians, as x then y per point. Segments are one long. */
    fun dragon(turns: IntArray, angle: Double): FloatArray {
        val out = FloatArray((turns.size + 2) * 2)
        var x = 0.0
        var y = 0.0
        var heading = 0.0
        for (i in 0..turns.size) {
            out[2 * i] = x.toFloat()
            out[2 * i + 1] = y.toFloat()
            x += cos(heading)
            y += sin(heading)
            if (i < turns.size) heading += turns[i] * angle
        }
        out[2 * turns.size + 2] = x.toFloat()
        out[2 * turns.size + 3] = y.toFloat()
        return out
    }
}
