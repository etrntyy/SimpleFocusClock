package com.focusclock.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlin.math.max

@Composable
internal fun StatsScreen(log: FocusLog, settings: ClockSettings, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    var period by remember { mutableStateOf(Period.Week) }
    val sessions = log.sessions
    val report = remember(period, sessions) { FocusStats.report(period, sessions, System.currentTimeMillis(), ZoneId.systemDefault()) }
    val target = settings.target(period)
    // A bar is a day for a week or month, a month for a year and a year for all time, so each is held to the next target down.
    val line = when (period) {
        Period.Day -> 0
        Period.Week, Period.Month -> settings.targetDaily
        Period.Year -> settings.targetMonthly
        Period.All -> settings.targetYearly
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(remember { ScrollState(0) })
            .padding(start = (25 * u).dp, end = (25 * u).dp, top = (36 * u).dp, bottom = navBottom + (40 * u).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Period.entries.forEach { p ->
                val on = p == period
                Text(
                    p.tab, color = Color.White, fontSize = (21 * u).sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Light,
                    modifier = Modifier.clickable { period = p }
                        .drawBehind { if (on) drawLine(Color.White, Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) }
                        .padding(bottom = (8 * u).dp),
                )
            }
        }
        Spacer(Modifier.height((50 * u).dp))
        BasicText(
            FocusStats.duration(report.totalMinutes), maxLines = 1,
            style = TextStyle(color = Color.White, fontWeight = FontWeight.Light, textAlign = TextAlign.Center),
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = (70 * u).sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (period) {
                Period.Day -> "focus hours today"
                Period.Week -> "focus hours this week"
                Period.Month -> "focus hours this month"
                Period.Year -> "focus hours this year"
                Period.All -> "focus hours in total"
            },
            color = Color.White, fontSize = (28 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = (12 * u).dp),
        )
        if (target > 0) {
            Text("target ${FocusStats.target(target)}", color = Color.White, fontSize = (21 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = (8 * u).dp))
        }
        Spacer(Modifier.height((44 * u).dp))
        if (period == Period.All) DaysGrid(u, sessions) else BarChart(u, report, line)
        Spacer(Modifier.height((30 * u).dp))
        Row(Modifier.fillMaxWidth()) {
            Figure(u, report.sessions.toString(), "Sessions", Modifier.weight(1f))
            Figure(u, FocusStats.duration(report.longestMinutes), "Longest", Modifier.weight(1f))
            Figure(u, report.thirdValue, report.thirdLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(u: Float, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = (26 * u).sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
        Text(label, color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = (8 * u).dp))
    }
}

/**
 * One bar per hour, day, month or year. It is wider than the screen when it needs to be, scrolls sideways and opens with
 * the present moment at the right edge. A dashed line marks the [line] target, in minutes, for whatever one bar stands for.
 */
@Composable
private fun BarChart(u: Float, report: Report, line: Int) {
    val perHour = report.period == Period.Day
    val plot = (250 * u).dp
    // Headroom above the tallest bar for its figure.
    val top = max(max(report.bars.maxOf { it.minutes }, line.toDouble()), 60.0) * 1.15
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = maxWidth
        val slot = when (report.period) {
            Period.Day -> (100 * u).dp
            Period.Week -> viewport / 7
            Period.Month, Period.Year -> (56 * u).dp
            Period.All -> (80 * u).dp
        }
        val scroll = remember(report.period) { ScrollState(0) }
        val dens = LocalDensity.current
        LaunchedEffect(report.period, report.bars.size) {
            withFrameNanos { }
            val end = with(dens) { ((report.current + 1) * slot.toPx() - viewport.toPx()) }
            scroll.scrollTo(max(0f, end).toInt())
        }
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.horizontalScroll(scroll)) {
                Row(
                    Modifier.widthIn(min = viewport).drawBehind {
                        val y = plot.toPx()
                        drawLine(Color.White, Offset(0f, y), Offset(size.width, y), 1.2f * u * dens.density)
                    },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    report.bars.forEach { bar ->
                        Column(Modifier.width(slot), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(plot).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val figure = FocusStats.barValue(bar.minutes, perHour)
                                    if (figure.isNotEmpty()) {
                                        Text(figure, color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = (5 * u).dp))
                                    }
                                    val h = if (bar.minutes > 0) maxOf(plot * (bar.minutes / top).toFloat(), 2.dp) else 0.dp
                                    Box(Modifier.width(slot * 0.7f).height(h).background(Color.White))
                                }
                            }
                            Text(bar.label, color = Color.White, fontSize = (19 * u).sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = (10 * u).dp))
                        }
                    }
                }
            }
            if (line > 0) {
                val at = plot * (1f - (line / top).toFloat())
                Canvas(Modifier.fillMaxWidth().height(plot)) {
                    val y = at.toPx()
                    drawLine(Color.White, Offset(0f, y), Offset(size.width, y), 1.5f * u * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u * density, 8f * u * density)))
                }
                // Stays put at the right edge while the bars scroll underneath.
                Text(
                    FocusStats.target(line), color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = at - (24 * u).dp).background(Color.Black).padding(horizontal = (3 * u).dp),
                )
            }
        }
    }
}
