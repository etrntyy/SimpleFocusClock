package com.focusclock.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The clock faces that are drawn rather than typed. Each is white on black, so the light theme simply flips it. */
enum class AnalogStyle(val label: String) {
    Disc("Disc"), RingTick("Ring tick"), Hands("Hands"), Dots("Dots"), Ticks("Ticks"), Numerals("Numerals"),
    DotRing("Dot ring"), Rings("Rings"), Segments("Segments"), Orbit("Orbit"), Dial("Dial"), Sweep("Sweep"),
    Eclipse("Eclipse"), Levels("Levels"), Linear("Linear"),
}

/** Where everything stands on a face at one moment: angles in degrees clockwise from 12, fractions from 0 to 1. */
data class FaceTime(val hourDeg: Float, val minuteDeg: Float, val secondDeg: Float, val hourFraction: Float, val minuteFraction: Float, val secondFraction: Float, val hour12: Float, val minute: Int) {
    companion object {
        fun of(hour: Int, minute: Int, second: Int): FaceTime {
            val m = minute + second / 60f
            val h = hour % 12 + m / 60f
            return FaceTime(h * 30f, m * 6f, second * 6f, h / 12f, m / 60f, second / 60f, h, minute)
        }
    }
}

private val Dim = Color(0xFF2A2A2A)
private val Faint = Color(0xFF444444)
private val Track = Color(0xFF333333)

/**
 * Draws [style] for [time] as large as the space allows. The seconds hand or ring appears only when [showSeconds] is on;
 * the styles that write digits use the chosen clock [font] and [use24h].
 */
@Composable
fun AnalogClock(style: AnalogStyle, time: LocalDateTime, showSeconds: Boolean, use24h: Boolean, font: ClockFont, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val t = FaceTime.of(time.hour, time.minute, if (showSeconds) time.second else 0)
    val hh = "%02d".format(if (use24h) time.hour else (time.hour % 12).let { if (it == 0) 12 else it })
    val mm = "%02d".format(time.minute)
    Canvas(modifier) {
        val side = min(size.width, size.height) * 0.94f
        val k = side / 200f
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        fun p(x: Float, y: Float) = Offset(origin.x + x * k, origin.y + y * k)
        fun at(deg: Float, r: Float): Offset {
            val a = deg * PI.toFloat() / 180f
            return p(100f + r * sin(a), 100f - r * cos(a))
        }
        fun line(deg: Float, from: Float, to: Float, w: Float, color: Color = Color.White) =
            drawLine(color, at(deg, from), at(deg, to), w * k, StrokeCap.Round)
        fun ring(r: Float, w: Float, color: Color = Color.White) = drawCircle(color, r * k, p(100f, 100f), style = Stroke(w * k))
        fun arc(r: Float, w: Float, startDeg: Float, sweepDeg: Float, color: Color = Color.White) =
            drawArc(color, startDeg - 90f, sweepDeg, false, p(100f - r, 100f - r), Size(2 * r * k, 2 * r * k), style = Stroke(w * k))
        fun dot(deg: Float, r: Float, size: Float, color: Color = Color.White) = drawCircle(color, size * k, at(deg, r))
        fun disc(r: Float, color: Color = Color.White) = drawCircle(color, r * k, p(100f, 100f))
        fun write(text: String, cx: Float, cy: Float, fontSize: Float, weight: FontWeight = FontWeight.Light) {
            val layout = measurer.measure(text, TextStyle(color = Color.White, fontFamily = font.family, fontSize = (fontSize * k).toSp(), fontWeight = weight))
            // Centre on the digits themselves, which sit between the baseline and the cap height.
            drawText(layout, topLeft = Offset(p(cx, cy).x - layout.size.width / 2f, p(cx, cy).y - (layout.firstBaseline - 0.36f * fontSize * k)))
        }
        fun hands(hourLen: Float, minuteLen: Float, secondTail: Float = -12f, secondLen: Float = 78f) {
            line(t.hourDeg, 0f, hourLen, 5f)
            line(t.minuteDeg, 0f, minuteLen, 3.4f)
            if (showSeconds) line(t.secondDeg, secondTail, secondLen, 1.2f)
            disc(4.2f)
        }

        when (style) {
            AnalogStyle.Disc -> {
                disc(90f)
                line(t.hourDeg, 90f, 66f, 8f, Color.Black)
                line(t.minuteDeg, 90f, 44f, 4.5f, Color.Black)
                if (showSeconds) line(t.secondDeg, 90f, 30f, 1.6f, Color.Black)
            }
            AnalogStyle.RingTick -> {
                ring(89f, 5f)
                line(t.hourDeg, 82f, 60f, 7f)
                line(t.minuteDeg, 82f, 42f, 4f)
                if (showSeconds) line(t.secondDeg, 82f, 30f, 1.5f)
            }
            AnalogStyle.Hands -> {
                ring(90f, 2f)
                hands(50f, 74f, -14f, 82f)
            }
            AnalogStyle.Dots -> {
                for (i in 0 until 12) dot(i * 30f, 86f, if (i % 3 == 0) 4.5f else 2.6f)
                hands(48f, 70f, -12f, 76f)
            }
            AnalogStyle.Ticks -> {
                for (i in 0 until 60) if (i % 5 == 0) line(i * 6f, 78f, 92f, 2.2f) else line(i * 6f, 86f, 92f, 1f)
                hands(44f, 70f, -12f, 78f)
            }
            AnalogStyle.Numerals -> {
                write("12", 100f, 30f, 22f); write("3", 170f, 100f, 22f); write("6", 100f, 170f, 22f); write("9", 30f, 100f, 22f)
                line(t.hourDeg, 0f, 44f, 5f)
                line(t.minuteDeg, 0f, 62f, 3.2f)
                if (showSeconds) line(t.secondDeg, -10f, 66f, 1.2f)
                disc(4f)
            }
            AnalogStyle.DotRing -> {
                for (i in 0 until 60) dot(i * 6f, 88f, if (i <= t.minute) 2.6f else 2f, if (i <= t.minute) Color.White else Track)
                val hour = t.hour12.toInt()
                for (i in 0 until 12) dot(i * 30f, 60f, if (i <= hour) 4f else 3f, if (i <= hour) Color.White else Track)
            }
            AnalogStyle.Rings -> {
                ring(88f, 7f, Dim); ring(74f, 7f, Dim)
                arc(88f, 7f, 0f, 360f * t.hourFraction)
                arc(74f, 7f, 0f, 360f * t.minuteFraction)
                if (showSeconds) {
                    ring(60f, 7f, Dim)
                    arc(60f, 7f, 0f, 360f * t.secondFraction)
                }
            }
            AnalogStyle.Segments -> {
                for (i in 0 until 12) {
                    arc(80f, 12f, i * 30f + 2f, 26f, Dim)
                    val f = (t.hour12 - i).coerceIn(0f, 1f)
                    if (f > 0f) arc(80f, 12f, i * 30f + 2f, 26f * f)
                }
                write(mm, 100f, 100f, 44f)
            }
            AnalogStyle.Orbit -> {
                ring(84f, 1.5f, Faint); ring(54f, 1.5f, Faint)
                dot(t.minuteDeg, 84f, 6.5f)
                dot(t.hourDeg, 54f, 5.5f)
                write("$hh:$mm", 100f, 100f, 30f)
            }
            AnalogStyle.Dial -> {
                ring(90f, 3f, Dim)
                arc(90f, 3f, 0f, 360f * (if (showSeconds) t.secondFraction else t.minuteFraction))
                write("$hh:$mm", 100f, 100f, 62f)
            }
            AnalogStyle.Sweep -> {
                ring(74f, 2f)
                drawArc(Color.White, -90f, 360f * t.minuteFraction, true, p(100f - 74f, 100f - 74f), Size(148f * k, 148f * k))
                dot(t.hourDeg, 92f, 6f)
            }
            AnalogStyle.Eclipse -> {
                val r = 70f
                disc(r)
                val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(100f - r, 100f - r), Size(2 * r * k, 2 * r * k))) }
                clipPath(path) { drawCircle(Color.Black, r * k, p(100f + 2f * r * t.minuteFraction, 100f)) }
                ring(r, 1.5f, Color(0xFF555555))
                dot(t.hourDeg, 90f, 6f)
            }
            AnalogStyle.Levels -> {
                val height = 150f
                listOf(52f to t.hourFraction, 118f to t.minuteFraction).forEach { (x, f) ->
                    drawRoundRect(Dim, p(x, 25f), Size(30f * k, height * k), androidx.compose.ui.geometry.CornerRadius(15f * k))
                    val h = height * max(0.04f, f)
                    drawRoundRect(Color.White, p(x, 25f + height - h), Size(30f * k, h * k), androidx.compose.ui.geometry.CornerRadius(15f * k))
                }
            }
            AnalogStyle.Linear -> {
                listOf(76f to t.hourFraction, 124f to t.minuteFraction).forEach { (y, f) ->
                    drawLine(Track, p(26f, y), p(174f, y), 3f * k, StrokeCap.Round)
                    drawLine(Color.White, p(26f, y), p(26f + 148f * f, y), 3f * k, StrokeCap.Round)
                    drawCircle(Color.White, 9f * k, p(26f + 148f * f, y))
                }
            }
        }
    }
}
