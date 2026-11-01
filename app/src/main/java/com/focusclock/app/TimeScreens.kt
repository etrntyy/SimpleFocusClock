package com.focusclock.app

import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

private val Light = FontWeight.Light

@Composable
private fun Label(text: String, size: Float, modifier: Modifier = Modifier, dim: Boolean = false, align: TextAlign? = null, underline: Boolean = false) {
    val u = scale()
    Text(
        text, color = Color.White.copy(alpha = if (dim) 0.6f else 1f), fontSize = (size * u).sp, fontWeight = Light, modifier = modifier, textAlign = align,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

/** The back arrow and screen name every tool screen starts with. */
@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    val u = scale()
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = (12 * u).dp, top = (14 * u).dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White,
            modifier = Modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onBack).padding((10 * u).dp),
        )
        Text(title, color = Color.White, fontSize = (21 * u).sp, fontWeight = Light, letterSpacing = (1.5 * u).sp, modifier = Modifier.padding(start = (6 * u).dp))
    }
}

/** A white outline circle button. */
@Composable
internal fun OutlineCircle(size: Dp, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val u = scale()
    Box(
        modifier.size(size).clip(CircleShape).clickable(onClick = onClick).border((2.5 * u).dp, Color.White, CircleShape).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The Start button's double ring, for the main action of a screen. */
@Composable
internal fun DoubleRing(size: Dp, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val u = scale()
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description }.drawBehind {
            val ring = 4.5f * u * density
            val outer = size.toPx() / 2
            drawCircle(Color.White, radius = outer - ring / 2, style = Stroke(ring))
            drawCircle(Color.White, radius = outer * 0.845f - ring / 2, style = Stroke(ring))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun PillButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val u = scale()
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.Black).border((2 * u).dp, Color.White, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = (22 * u).dp, vertical = (9 * u).dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) Color.Black else Color.White, fontSize = (19 * u).sp, fontWeight = if (filled) FontWeight.Normal else Light, maxLines = 1) }
}

/** Two pause bars, or a play triangle. */
@Composable
private fun PlayOrPause(running: Boolean, size: Dp) {
    if (running) {
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.14f)) {
            repeat(2) { Box(Modifier.size(width = size * 0.16f, height = size * 0.5f).background(Color.White)) }
        }
    } else {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.75f))
    }
}

// Icons the core icon set lacks, drawn in the same thin white line as the rest of the app.

@Composable
internal fun HourglassIcon(size: Dp, modifier: Modifier = Modifier) {
    val u = scale()
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = Stroke(2.6f * u * density, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.2f, h * 0.08f), Offset(w * 0.8f, h * 0.08f), s.width, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.2f, h * 0.92f), Offset(w * 0.8f, h * 0.92f), s.width, StrokeCap.Round)
        listOf(0.26f to 0.74f).forEach { (l, r) ->
            drawLine(Color.White, Offset(w * l, h * 0.08f), Offset(w * 0.5f, h * 0.5f), s.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * r, h * 0.08f), Offset(w * 0.5f, h * 0.5f), s.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * l, h * 0.92f), Offset(w * 0.5f, h * 0.5f), s.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * r, h * 0.92f), Offset(w * 0.5f, h * 0.5f), s.width, StrokeCap.Round)
        }
    }
}

@Composable
internal fun StopwatchIcon(size: Dp, modifier: Modifier = Modifier) {
    val u = scale()
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.6f * u * density
        drawCircle(Color.White, radius = w * 0.37f, center = Offset(w / 2, h * 0.57f), style = Stroke(sw))
        drawLine(Color.White, Offset(w * 0.4f, h * 0.07f), Offset(w * 0.6f, h * 0.07f), sw, StrokeCap.Round)
        drawLine(Color.White, Offset(w / 2, h * 0.07f), Offset(w / 2, h * 0.2f), sw, StrokeCap.Round)
        drawLine(Color.White, Offset(w / 2, h * 0.57f), Offset(w * 0.64f, h * 0.43f), sw, StrokeCap.Round)
    }
}

/** The icon on the home screen that opens the tools. */
@Composable
internal fun ToolsIcon(onClick: () -> Unit, modifier: Modifier) {
    val u = scale()
    Box(modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Timer, stopwatch and alarms" }, contentAlignment = Alignment.Center) {
        HourglassIcon((30 * u).dp)
    }
}

@Composable
internal fun ToolsScreen(schedule: Schedule, stopwatch: Stopwatch, use24h: Boolean, onTimer: () -> Unit, onStopwatch: () -> Unit, onAlarms: () -> Unit, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val timer = schedule.timer
    val next = schedule.alarms.mapNotNull { it.next(now, zone) }.minOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Time tools", onBack)
        Spacer(Modifier.height((30 * u).dp))
        ToolRow("Timer", when {
            timer == null -> "Count down on the full screen"
            timer.done -> "Time’s up"
            timer.running -> "Running"
            else -> "Paused at ${TimeFormat.countdown(timer.pausedLeft)}"
        }, onTimer) { HourglassIcon((38 * u).dp) }
        ToolRow("Stopwatch", if (stopwatch.running) "Running" else if (stopwatch.base > 0) "Stopped at ${TimeFormat.stopwatch(stopwatch.base).first}" else "Laps, full screen", onStopwatch) { StopwatchIcon((38 * u).dp) }
        ToolRow("Alarms", next?.let { "Next: ${TimeFormat.whenText(it, now, zone, use24h)}" } ?: "Alarms and reminders", onAlarms) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size((38 * u).dp))
        }
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val u = scale()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = (28 * u).dp, vertical = (22 * u).dp)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width((50 * u).dp), contentAlignment = Alignment.CenterStart) { icon() }
        Column(Modifier.weight(1f)) {
            Label(title, 30f)
            Label(subtitle, 16f, dim = true)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size((32 * u).dp))
    }
}

private fun lerp3(d: Float, a: Float, b: Float, c: Float) = when {
    d <= 1f -> a + (b - a) * d
    d <= 2f -> b + (c - b) * (d - 1f)
    else -> c
}

/**
 * A column of values that scrolls round and snaps. The middle one is the choice: it is full size and bright, and the rows
 * above and below shrink and dim with their distance from it.
 */
@Composable
internal fun Wheel(values: List<String>, selected: Int, width: Dp, big: Float = 60f, onSelect: (Int) -> Unit) {
    val u = scale()
    val row = (58 * u).dp
    val rowPx = with(LocalDensity.current) { row.toPx() }
    val count = values.size
    val start = remember(count) { Int.MAX_VALUE / 2 / count * count }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start + selected - 2)
    val current by rememberUpdatedState(selected)
    val report by rememberUpdatedState(onSelect)
    LaunchedEffect(state) {
        snapshotFlow { if (state.isScrollInProgress) -1 else state.firstVisibleItemIndex + (if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0) + 2 }
            .collect { centre -> if (centre >= 0 && centre % count != current) report(centre % count) }
    }
    // A choice made somewhere else (a preset) turns the wheel to it.
    LaunchedEffect(selected) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val centre = state.firstVisibleItemIndex + 2
        val delta = ((selected - centre % count) % count + count) % count
        val step = if (delta > count / 2) delta - count else delta
        if (step != 0) state.animateScrollToItem(state.firstVisibleItemIndex + step)
    }
    LazyColumn(Modifier.width(width).height(row * 5), state = state, flingBehavior = rememberSnapFlingBehavior(state)) {
        items(Int.MAX_VALUE) { index ->
            Box(
                Modifier.fillMaxWidth().height(row).graphicsLayer {
                    val info = state.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                    val d = if (item == null) 3f else abs(item.offset + item.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f) / item.size
                    val s = lerp3(d, 1f, 0.65f, 0.45f)
                    scaleX = s
                    scaleY = s
                    alpha = lerp3(d, 1f, 0.4f, 0.15f)
                },
                contentAlignment = Alignment.Center,
            ) { Text(values[index % count], color = Color.White, fontSize = (big * u).sp, fontWeight = Light, maxLines = 1) }
        }
    }
}

private fun twoDigits(n: Int) = (0 until n).map { "%02d".format(it) }

@Composable
internal fun TimerSetupScreen(onStart: (Long) -> Unit, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(25) }
    var s by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(false) }
    val total = (h * 3600L + m * 60L + s) * 1000L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header("Timer", onBack)
        Spacer(Modifier.height((26 * u).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((6 * u).dp)) {
            listOf("hours", "minutes", "seconds").forEach { Label(it, 17f, Modifier.width((92 * u).dp), dim = true, align = TextAlign.Center) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy((6 * u).dp)) {
            Wheel(twoDigits(24), h, (92 * u).dp) { h = it; hint = false }
            Wheel(twoDigits(60), m, (92 * u).dp) { m = it; hint = false }
            Wheel(twoDigits(60), s, (92 * u).dp) { s = it; hint = false }
        }
        Spacer(Modifier.height((22 * u).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((10 * u).dp)) {
            listOf(5, 10, 25, 45).forEach { min ->
                PillButton("${min}m", total == min * 60_000L, { h = 0; m = min; s = 0; hint = false })
            }
        }
        Spacer(Modifier.height((30 * u).dp))
        DoubleRing((150 * u).dp, "Start timer", { if (total > 0) onStart(total) else hint = true }) { Label("Start", 30f) }
        Label(if (hint) "Choose a time first" else "", 17f, Modifier.padding(top = (12 * u).dp, bottom = (24 * u).dp), dim = true)
    }
}

/** The running timer, full screen like the clock. A finished timer is shown by [RingingScreen] on top of it. */
@Composable
internal fun TimerScreen(settings: ClockSettings, schedule: Schedule, onExit: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onExit)
    val timer = schedule.timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        now = System.currentTimeMillis()
        while (timer?.running == true) {
            delay(100)
            now = System.currentTimeMillis()
        }
    }
    // The timer was stopped from the ringing screen or the notification.
    LaunchedEffect(timer == null) { if (timer == null) onExit() }
    val t = timer ?: return
    val left = t.left(now)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding((12 * u).dp)) {
        val portrait = maxHeight > maxWidth
        Label("Timer", 22f, Modifier.padding(start = (16 * u).dp, top = (10 * u).dp))
        Icon(
            Icons.Filled.Close, contentDescription = "Leave the timer running", tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).size((48 * u).dp).clip(CircleShape).clickable(onClick = onExit).padding((8 * u).dp),
        )
        val digits = @Composable { modifier: Modifier ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ClockFace(listOf(TimeFormat.countdown(left)), ClockFonts[settings.fontIndex], Modifier.fillMaxWidth().weight(1f))
                Box(Modifier.padding(top = (14 * u).dp).fillMaxWidth(0.62f).height((3 * u).dp).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth((left.toFloat() / t.totalMs).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
                }
                Label(
                    if (t.running) "Ends at " + Instant.ofEpochMilli(t.endAt).atZone(ZoneId.systemDefault()).let { TimeFormat.time(it.hour, it.minute, settings.use24h) } else "Paused",
                    17f, Modifier.padding(top = (12 * u).dp), dim = true,
                )
            }
        }
        val controls = @Composable {
            OutlineCircle((60 * u).dp, "Cancel timer", { schedule.clearTimer() }) { Label("Cancel", 15f) }
            DoubleRing((100 * u).dp, if (t.running) "Pause" else "Resume", { if (t.running) schedule.pauseTimer() else schedule.resumeTimer() }) { PlayOrPause(t.running, (44 * u).dp) }
            OutlineCircle((60 * u).dp, "Add one minute", { schedule.addToTimer(60_000L) }) { Label("+1m", 19f) }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize().padding(top = (50 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                digits(Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.padding(vertical = (30 * u).dp), horizontalArrangement = Arrangement.spacedBy((30 * u).dp), verticalAlignment = Alignment.CenterVertically) { controls() }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(top = (40 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                digits(Modifier.weight(1.4f).fillMaxHeight())
                Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((16 * u).dp)) { controls() }
            }
        }
    }
}

@Composable
internal fun StopwatchScreen(settings: ClockSettings, stopwatch: Stopwatch, onChange: (Stopwatch) -> Unit, onExit: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onExit)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(stopwatch) {
        now = SystemClock.elapsedRealtime()
        while (stopwatch.running) {
            delay(30)
            now = SystemClock.elapsedRealtime()
        }
    }
    val elapsed = stopwatch.elapsed(now)
    val (main, hundredths) = TimeFormat.stopwatch(elapsed)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding((12 * u).dp)) {
        val portrait = maxHeight > maxWidth
        Label("Stopwatch", 22f, Modifier.padding(start = (16 * u).dp, top = (10 * u).dp))
        Icon(
            Icons.Filled.Close, contentDescription = "Leave the stopwatch", tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).size((48 * u).dp).clip(CircleShape).clickable(onClick = onExit).padding((8 * u).dp),
        )
        val digits = @Composable { modifier: Modifier ->
            Row(modifier, verticalAlignment = Alignment.Bottom) {
                ClockFace(listOf(main), ClockFonts[settings.fontIndex], Modifier.weight(1f).fillMaxHeight())
                Label(".$hundredths", 36f, Modifier.padding(start = (4 * u).dp, bottom = (6 * u).dp), dim = true)
            }
        }
        val controls = @Composable {
            if (stopwatch.running) OutlineCircle((64 * u).dp, "Lap", { onChange(stopwatch.lap(SystemClock.elapsedRealtime())) }) { Label("Lap", 20f) }
            else Spacer(Modifier.size((64 * u).dp))
            DoubleRing((100 * u).dp, if (stopwatch.running) "Pause" else "Start", {
                val t = SystemClock.elapsedRealtime()
                onChange(if (stopwatch.running) stopwatch.pause(t) else stopwatch.start(t))
            }) { PlayOrPause(stopwatch.running, (44 * u).dp) }
            if (elapsed > 0) OutlineCircle((64 * u).dp, "Reset", { onChange(Stopwatch()) }) { Label("Reset", 18f) }
            else Spacer(Modifier.size((64 * u).dp))
        }
        val splits = stopwatch.splits()
        val laps = @Composable { modifier: Modifier ->
            val rowHeight = (56 * u).dp
            BoxWithConstraints(modifier) {
                LazyColumn(Modifier.fillMaxSize().fadeToBottom(0.5f), contentPadding = PaddingValues(bottom = (maxHeight - rowHeight).coerceAtLeast(0.dp))) {
                    items(splits.indices.reversed().toList()) { i ->
                        Row(
                            Modifier.fillMaxWidth().height(rowHeight).drawBehind { drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Label("Lap ${i + 1}", 22f, Modifier.weight(1f), dim = true)
                            Label(TimeFormat.stopwatch(splits[i]).let { "${it.first}.${it.second}" }, 22f, Modifier.weight(1f), align = TextAlign.Center)
                            Label(TimeFormat.stopwatch(stopwatch.laps[i]).let { "${it.first}.${it.second}" }, 22f, Modifier.weight(1f), dim = true, align = TextAlign.End)
                        }
                    }
                }
            }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize().padding(top = (50 * u).dp, start = (16 * u).dp, end = (16 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                digits(Modifier.fillMaxWidth().height((190 * u).dp))
                Row(Modifier.padding(vertical = (20 * u).dp), horizontalArrangement = Arrangement.spacedBy((30 * u).dp), verticalAlignment = Alignment.CenterVertically) { controls() }
                laps(Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(top = (40 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    digits(Modifier.fillMaxWidth().weight(1f))
                    Row(Modifier.padding(vertical = (12 * u).dp), horizontalArrangement = Arrangement.spacedBy((24 * u).dp), verticalAlignment = Alignment.CenterVertically) { controls() }
                }
                laps(Modifier.weight(0.8f).fillMaxHeight().padding(start = (16 * u).dp))
            }
        }
    }
}

// ---- Alarms and reminders ----

private const val ALARM_ROW = 100f
private const val REMINDER_ROW = 92f

@Composable
internal fun AlarmsScreen(
    schedule: Schedule,
    use24h: Boolean,
    onPickTone: (String, (String) -> Unit) -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val u = scale()
    var tab by remember { mutableIntStateOf(0) }
    var alarmDraft by remember { mutableStateOf<Alarm?>(null) }
    var reminderDraft by remember { mutableStateOf<Reminder?>(null) }
    BackHandler { if (alarmDraft != null) alarmDraft = null else if (reminderDraft != null) reminderDraft = null else onBack() }

    alarmDraft?.let { draft ->
        AlarmEditor(draft, schedule.alarms.any { it.id == draft.id }, use24h, onPickTone, { alarmDraft = null }, { schedule.deleteAlarm(draft.id); alarmDraft = null }) {
            schedule.saveAlarm(it)
            alarmDraft = null
            onSaved()
        }
        return
    }
    reminderDraft?.let { draft ->
        ReminderEditor(draft, schedule.reminders.any { it.id == draft.id }, use24h, { reminderDraft = null }, { schedule.deleteReminder(draft.id); reminderDraft = null }) {
            schedule.saveReminder(it)
            reminderDraft = null
            onSaved()
        }
        return
    }

    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = (12 * u).dp, top = (14 * u).dp, end = (25 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White,
                    modifier = Modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onBack).padding((10 * u).dp),
                )
                Spacer(Modifier.weight(1f))
                listOf("Alarms", "Reminders").forEachIndexed { i, name ->
                    val on = i == tab
                    Text(
                        name, color = Color.White, fontSize = (21 * u).sp, fontWeight = if (on) FontWeight.Medium else Light,
                        modifier = Modifier.padding(start = (22 * u).dp).clickable { tab = i }
                            .drawBehind { if (on) drawLine(Color.White, Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) }
                            .padding(bottom = (8 * u).dp),
                    )
                }
            }
            Spacer(Modifier.height((18 * u).dp))
            if (tab == 0) {
                if (schedule.alarms.isEmpty()) Empty("No alarms yet") else FadingList(ALARM_ROW) {
                    items(schedule.alarms, key = { it.id }) { a ->
                        val (time, ap) = TimeFormat.alarmTime(a.hour, a.minute, use24h)
                        Row(
                            Modifier.fillMaxWidth().height((ALARM_ROW * u).dp).clickable { alarmDraft = a }.padding(horizontal = (28 * u).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).graphicsLayer { alpha = if (a.enabled) 1f else 0.45f }) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Label(time, 54f)
                                    Label(ap, 22f, Modifier.padding(start = (8 * u).dp, bottom = (8 * u).dp))
                                }
                                Label(listOf(TimeFormat.days(a.days), a.label).filter { it.isNotBlank() }.joinToString(" · "), 17f, dim = true)
                            }
                            Toggle(a.enabled) { schedule.saveAlarm(a.copy(enabled = it)); if (it) onSaved() }
                        }
                    }
                }
            } else {
                if (schedule.reminders.isEmpty()) Empty("No reminders yet") else FadingList(REMINDER_ROW) {
                    items(schedule.reminders, key = { it.id }) { r ->
                        Row(
                            Modifier.fillMaxWidth().height((REMINDER_ROW * u).dp).clickable { reminderDraft = r }.padding(horizontal = (28 * u).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Label(r.text.ifBlank { "Reminder" }, 27f)
                                Label(
                                    TimeFormat.whenText(r.at, now, zone, use24h) + if (r.repeat != Repeat.Never) " · ${r.repeat.label}" else "",
                                    17f, dim = true,
                                )
                            }
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size((28 * u).dp))
                        }
                    }
                }
            }
        }
        OutlineCircle(
            (64 * u).dp, if (tab == 0) "New alarm" else "New reminder",
            {
                val soon = LocalTime.now().plusHours(1)
                if (tab == 0) alarmDraft = Alarm(System.currentTimeMillis(), soon.hour, 0)
                else reminderDraft = Reminder(System.currentTimeMillis(), "", Instant.now().plusSeconds(3600).let { it.toEpochMilli() / 60_000 * 60_000 })
            },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = (24 * u).dp).background(Color.Black, CircleShape),
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size((34 * u).dp)) }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(text, 24f, dim = true) }
}

/** A list that fades toward the bottom and can scroll until its last row sits where the first one starts. */
@Composable
private fun FadingList(rowHeight: Float, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val u = scale()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().fadeToBottom(0.8f), contentPadding = PaddingValues(bottom = (maxHeight - (rowHeight * u).dp).coerceAtLeast(0.dp)), content = content)
    }
}

@Composable
private fun TextLine(value: String, hint: String, fontSize: Float, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val u = scale()
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = (fontSize * u).sp, fontWeight = Light),
        cursorBrush = SolidColor(Color.White),
        decorationBox = { inner ->
            Box(Modifier.padding(bottom = (6 * u).dp)) {
                if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.4f), fontSize = (fontSize * u).sp, fontWeight = Light)
                inner()
            }
        },
        modifier = modifier.drawBehind { drawLine(Color.White, Offset(0f, size.height), Offset(size.width, size.height), 1.5f * u * density) },
    )
}

@Composable
private fun FieldRow(label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val u = scale()
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = (14 * u).dp)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, 22f, Modifier.weight(1f), dim = true)
        trailing()
    }
}

/** Hour, minute and (in 12-hour time) am/pm wheels. */
@Composable
private fun TimeWheels(hour: Int, minute: Int, use24h: Boolean, big: Float, onChange: (Int, Int) -> Unit) {
    val u = scale()
    val hours = if (use24h) twoDigits(24) else (1..12).map { it.toString() }
    val shown = if (use24h) hour else (if (hour % 12 == 0) 12 else hour % 12) - 1
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Wheel(hours, shown, (92 * u).dp, big) { i ->
            onChange(if (use24h) i else (i + 1) % 12 + if (hour >= 12) 12 else 0, minute)
        }
        Text(":", color = Color.White, fontSize = (big * u).sp, fontWeight = Light)
        Wheel(twoDigits(60), minute, (92 * u).dp, big) { onChange(hour, it) }
        if (!use24h) {
            Column(Modifier.padding(start = (10 * u).dp), verticalArrangement = Arrangement.spacedBy((10 * u).dp)) {
                listOf("am" to false, "pm" to true).forEach { (name, pm) ->
                    val on = (hour >= 12) == pm
                    PillButton(name, on, { onChange(hour % 12 + if (pm) 12 else 0, minute) })
                }
            }
        }
    }
}

@Composable
private fun AlarmEditor(draft: Alarm, existing: Boolean, use24h: Boolean, onPickTone: (String, (String) -> Unit) -> Unit, onBack: () -> Unit, onDelete: () -> Unit, onSave: (Alarm) -> Unit) {
    val u = scale()
    val context = LocalContext.current
    var a by remember { mutableStateOf(draft) }
    val toneName = remember(a.tone) {
        runCatching { RingtoneManager.getRingtone(context, a.tone.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).getTitle(context) }.getOrNull() ?: "Default"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(if (existing) "Edit alarm" else "New alarm", onBack)
        Spacer(Modifier.height((16 * u).dp))
        TimeWheels(a.hour, a.minute, use24h, 56f) { h, m -> a = a.copy(hour = h, minute = m) }
        Spacer(Modifier.height((14 * u).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((7 * u).dp)) {
            "MTWTFSS".forEachIndexed { i, ch ->
                val on = a.days and (1 shl i) != 0
                Box(
                    Modifier.size((40 * u).dp).clip(CircleShape).background(if (on) Color.White else Color.Black).border((2 * u).dp, Color.White, CircleShape)
                        .clickable { a = a.copy(days = a.days xor (1 shl i)) }.semantics { contentDescription = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i] },
                    contentAlignment = Alignment.Center,
                ) { Text(ch.toString(), color = if (on) Color.Black else Color.White, fontSize = (18 * u).sp, fontWeight = Light) }
            }
        }
        Label(TimeFormat.days(a.days), 17f, Modifier.padding(top = (10 * u).dp), dim = true)
        Column(Modifier.fillMaxWidth().padding(horizontal = (28 * u).dp, vertical = (14 * u).dp)) {
            FieldRow("Label") { TextLine(a.label, "Gym", 22f, { a = a.copy(label = it) }, Modifier.width((180 * u).dp)) }
            FieldRow("Sound", { onPickTone(a.tone) { a = a.copy(tone = it) } }) { Label(toneName, 22f, align = TextAlign.End, modifier = Modifier.width((180 * u).dp)) }
            FieldRow("Snooze") {
                StepButton(plus = false) { a = a.copy(snooze = (a.snooze - 5).coerceAtLeast(5)) }
                Label("${a.snooze} min", 22f, Modifier.width((84 * u).dp), align = TextAlign.Center)
                StepButton(plus = true) { a = a.copy(snooze = (a.snooze + 5).coerceAtMost(30)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy((22 * u).dp), verticalAlignment = Alignment.CenterVertically) {
            if (existing) Label("Delete", 19f, Modifier.clickable(onClick = onDelete).padding((8 * u).dp), underline = true)
            PillButton("Save", true, { onSave(a.copy(enabled = true)) })
        }
        Spacer(Modifier.height((26 * u).dp))
    }
}

@Composable
private fun ReminderEditor(draft: Reminder, existing: Boolean, use24h: Boolean, onBack: () -> Unit, onDelete: () -> Unit, onSave: (Reminder) -> Unit) {
    val u = scale()
    val zone = ZoneId.systemDefault()
    val start = remember { Instant.ofEpochMilli(draft.at).atZone(zone) }
    val today = remember { LocalDate.now(zone) }
    var text by remember { mutableStateOf(draft.text) }
    var dayOffset by remember { mutableIntStateOf(java.time.temporal.ChronoUnit.DAYS.between(today, start.toLocalDate()).toInt().coerceAtLeast(0)) }
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    var repeat by remember { mutableStateOf(draft.repeat) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(if (existing) "Edit reminder" else "New reminder", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = (28 * u).dp, vertical = (20 * u).dp)) {
            TextLine(text, "What should it say", 32f, { text = it; error = false }, Modifier.fillMaxWidth())
        }
        TimeWheels(hour, minute, use24h, 52f) { h, m -> hour = h; minute = m; error = false }
        Column(Modifier.fillMaxWidth().padding(horizontal = (28 * u).dp, vertical = (14 * u).dp)) {
            FieldRow("Date") {
                StepButton(plus = false) { dayOffset = (dayOffset - 1).coerceAtLeast(0); error = false }
                Label(TimeFormat.date(today.plusDays(dayOffset.toLong()), today), 22f, Modifier.width((120 * u).dp), align = TextAlign.Center)
                StepButton(plus = true) { dayOffset += 1; error = false }
            }
            FieldRow("Repeat") {
                StepButton(plus = false) { repeat = Repeat.entries[(repeat.ordinal + Repeat.entries.size - 1) % Repeat.entries.size] }
                Label(repeat.label, 22f, Modifier.width((120 * u).dp), align = TextAlign.Center)
                StepButton(plus = true) { repeat = Repeat.entries[(repeat.ordinal + 1) % Repeat.entries.size] }
            }
        }
        Label(if (error) "Choose a time in the future" else "", 17f, Modifier.padding(bottom = (10 * u).dp), dim = true)
        Row(horizontalArrangement = Arrangement.spacedBy((22 * u).dp), verticalAlignment = Alignment.CenterVertically) {
            if (existing) Label("Delete", 19f, Modifier.clickable(onClick = onDelete).padding((8 * u).dp), underline = true)
            PillButton("Save", true, {
                val at = today.plusDays(dayOffset.toLong()).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                if (at <= System.currentTimeMillis()) error = true else onSave(Reminder(draft.id, text.trim(), at, repeat))
            })
        }
        Spacer(Modifier.height((26 * u).dp))
    }
}

// ---- On top of everything ----

/** An alarm or the timer going off: the whole screen, until it is stopped. */
@Composable
internal fun RingingScreen(ring: Ring, use24h: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    val u = scale()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1_000) } }
    BackHandler { }
    val timer = ring.kind == Kind.Timer
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { }.statusBarsPadding().navigationBarsPadding()) {
        val portrait = maxHeight > maxWidth
        val heading = @Composable {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!timer) Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size((34 * u).dp))
                Label(if (timer) "00:00" else TimeFormat.time(now.hour, now.minute, use24h), 100f, Modifier.padding(vertical = (8 * u).dp))
                Label(if (timer) "Time’s up" else ring.text.ifBlank { "Alarm" }, 34f, align = TextAlign.Center)
            }
        }
        val actions = @Composable {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DoubleRing((150 * u).dp, "Stop", onStop) { Label("Stop", 32f) }
                Label(if (timer) "Add 1 minute" else "Snooze ${ring.snooze} min", 21f, Modifier.clickable(onClick = onSnooze).padding((16 * u).dp), underline = true)
            }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize().padding(vertical = (40 * u).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                heading()
                actions()
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                heading()
                actions()
            }
        }
    }
}

/** A reminder over the clock: it stays for a minute, a tap snoozes it and the cross dismisses it. */
@Composable
internal fun ReminderBanner(ring: Ring, use24h: Boolean, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val u = scale()
    val firedAt = remember(ring) { LocalTime.now() }
    LaunchedEffect(ring) { delay(60_000); onDismiss() }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = (14 * u).dp, end = (14 * u).dp, top = (64 * u).dp)
            .clip(RoundedCornerShape((16 * u).dp)).background(Color.Black).border((1.8 * u).dp, Color.White, RoundedCornerShape((16 * u).dp))
            .clickable(onClick = onSnooze).padding(start = (16 * u).dp, top = (12 * u).dp, bottom = (12 * u).dp, end = (6 * u).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size((28 * u).dp))
        Column(Modifier.weight(1f).padding(horizontal = (12 * u).dp)) {
            Label(ring.text.ifBlank { "Reminder" }, 24f)
            Label("${TimeFormat.time(firedAt.hour, firedAt.minute, use24h)} · tap to snooze ${ring.snooze} min", 16f, dim = true)
        }
        Icon(
            Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White,
            modifier = Modifier.size((46 * u).dp).clip(CircleShape).clickable(onClick = onDismiss).padding((10 * u).dp),
        )
    }
}
