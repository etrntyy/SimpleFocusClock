package com.focusclock.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * Every day of the history as a square, a column for each week with Monday at the top. A day's square runs from a faint
 * dark grey for no focus to white at six hours. Tapping a day shows what was spent on it.
 */
@Composable
internal fun DaysGrid(u: Float, sessions: List<Session>) {
    val zone = ZoneId.systemDefault()
    val grid = remember(sessions) { FocusStats.dayGrid(sessions, System.currentTimeMillis(), zone) }
    var selected by remember(grid) { mutableIntStateOf(grid.minutes.size - 1) }
    val scroll = rememberScrollState()
    LaunchedEffect(grid) {
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cell = 22f * u
    val gap = 4f * u
    val label = 22f * u
    val step = cell + gap
    val width = (grid.columns * step - gap).dp
    val height = (label + 7 * step - gap).dp
    val monthStyle = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = (14 * u).sp, fontWeight = FontWeight.Light)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(top = label.dp).width((22 * u).dp), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                listOf("M", "", "W", "", "F", "", "S").forEach {
                    Box(Modifier.height(cell.dp), contentAlignment = Alignment.CenterStart) {
                        Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = (14 * u).sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(
                    Modifier.size(width, height).pointerInput(grid) {
                        detectTapGestures { at ->
                            val column = (at.x / (step * density.density)).toInt()
                            val row = ((at.y - label * density.density) / (step * density.density)).toInt()
                            val i = column * 7 + row
                            if (at.y >= label * density.density && row in 0..6 && i in grid.minutes.indices) selected = i
                        }
                    },
                ) {
                    val px = this.density
                    for (i in grid.minutes.indices) {
                        val g = FocusStats.heat(grid.minutes[i])
                        drawRoundRect(
                            Color(g, g, g), Offset(i / 7 * step * px, (label + i % 7 * step) * px), Size(cell * px, cell * px),
                            CornerRadius(5f * u * px),
                        )
                    }
                    // A ring around the chosen day.
                    val x = selected / 7 * step * px
                    val y = (label + selected % 7 * step) * px
                    val out = 2f * u * px
                    drawRoundRect(
                        Color.White, Offset(x - out, y - out), Size(cell * px + 2 * out, cell * px + 2 * out),
                        CornerRadius(7f * u * px), style = Stroke(2.5f * u * px),
                    )
                    // The month is written above the first week it starts in.
                    for (c in 0 until grid.columns) {
                        val monday = grid.date(c * 7)
                        if (monday.dayOfMonth <= 7) {
                            val text = monday.month.getDisplayName(DateStyle.SHORT, Locale.getDefault())
                            drawText(measurer, text, Offset(c * step * px, 0f), monthStyle)
                        }
                    }
                }
            }
        }
        val day = grid.date(selected)
        val today = grid.last
        val minutes = grid.minutes[selected]
        Text(
            if (day == today) "Today" else "${day.dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth} ${day.month.getDisplayName(DateStyle.SHORT, Locale.getDefault())} ${day.year}",
            color = Color.White, fontSize = (22 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = (22 * u).dp),
        )
        Text(
            if (minutes < 0.5) "No focus" else FocusStats.duration(minutes),
            color = Color.White, fontSize = (34 * u).sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth().padding(top = (22 * u).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((8 * u).dp)) {
            Text("0", color = Color.White.copy(alpha = 0.85f), fontSize = (14 * u).sp, fontWeight = FontWeight.Light)
            val low = FocusStats.heat(0.0)
            Box(
                Modifier.weight(1f).height((8 * u).dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(low, low, low), Color.White))),
            )
            Text("6h+", color = Color.White.copy(alpha = 0.85f), fontSize = (14 * u).sp, fontWeight = FontWeight.Light)
        }
    }
}
