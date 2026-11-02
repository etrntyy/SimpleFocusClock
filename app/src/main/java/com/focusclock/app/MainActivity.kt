package com.focusclock.app

import android.Manifest
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import android.graphics.Rect
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Canvas
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen { HOME, CLOCK, SETTINGS, STATS, TOOLS, TIMER_SET, TIMER, STOPWATCH, ALARMS, FRACTALS, FRACTAL }

class MainActivity : ComponentActivity() {
    private lateinit var settings: ClockSettings
    private lateinit var focus: Focus
    private lateinit var log: FocusLog
    private lateinit var schedule: Schedule
    private var stopwatch by mutableStateOf(Stopwatch())
    private var fractal by mutableStateOf<FractalChoice>(FractalChoice.Shuffle)
    private var toneResult: ((String) -> Unit)? = null
    private val noise by lazy { NoisePlayer(applicationContext, speakerToo = { settings.noiseSpeaker }, onEnded = { playing = null }) }
    private var screen by mutableStateOf(Screen.HOME)
    private var permissionTick by mutableStateOf(0)
    private var playing by mutableStateOf<NoiseKind?>(null)
    private var lastNoise = NoiseKind.White

    // The system's own folder picker, then Android's Do Not Disturb page: shown once, on the first launch.
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(log::chooseFolder)
        if (!settings.onboarded) {
            settings.onboarded = true
            if (!focus.canSilence) startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // The system's own sound picker for an alarm.
    private val pickTone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            toneResult?.invoke(uri?.toString().orEmpty())
        }
        toneResult = null
    }

    /** The screens where the phone stays awake, pinned in focus mode, and the time counts as focus. */
    private val immersive get() = screen == Screen.CLOCK || screen == Screen.TIMER || screen == Screen.STOPWATCH || screen == Screen.FRACTAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = ClockSettings(this)
        focus = Focus(this)
        log = FocusLog(this)
        schedule = Schedule.get(this)
        schedule.reschedule()
        showOverLock(intent)
        // The noise plays on the alarm stream so alarms-only Do Not Disturb doesn't mute it.
        volumeControlStream = AudioManager.STREAM_ALARM
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                val systemDark = isSystemInDarkTheme()
                val light = isLight(settings.appTheme, settings.clockTheme, systemDark, immersive)
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(if (light) android.graphics.Color.WHITE else android.graphics.Color.BLACK))
                    WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
                }
                // The GPU fractals flip themselves (black and white ones only), so the colour ones are never inverted.
                val flipHere = light && !(screen == Screen.FRACTAL && fractal.onGpu)
                Box(Modifier.fillMaxSize().inverted(flipHere).background(Color.Black)) {
                    when (screen) {
                        Screen.HOME -> Home(
                            playing = playing,
                            todayMinutes = remember(log.sessions, screen) {
                                FocusStats.report(Period.Day, log.sessions, System.currentTimeMillis(), ZoneId.systemDefault()).totalMinutes
                            },
                            dailyTarget = settings.targetDaily,
                            onStart = ::start,
                            onSettings = { screen = Screen.SETTINGS },
                            onStats = { screen = Screen.STATS },
                            onTools = { screen = Screen.TOOLS },
                            onFractals = { screen = Screen.FRACTALS },
                            onToggleNoise = ::toggleNoise,
                        )
                        Screen.CLOCK -> ClockScreen(
                            settings = settings,
                            focus = focus,
                            playing = playing != null,
                            onToggleSound = { toggleNoise(playing ?: lastNoise) },
                            onExit = ::stop,
                            onBeat = log::beat,
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            canSilence = permissionTick >= 0 && focus.canSilence,
                            onGrantSilence = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                            onSpeakerChange = { settings.updateNoiseSpeaker(it); if (playing != null) noise.refresh() },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.STATS -> StatsScreen(
                            log = log,
                            settings = settings,
                            onChooseFolder = { pickFolder.launch(null) },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.FRACTALS -> FractalsScreen(
                            mono = settings.fractalMono,
                            onMono = settings::updateFractalMono,
                            onPick = { fractal = it; enter(Screen.FRACTAL) },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.FRACTAL -> FractalScreen(fractal, settings.fractalMono, light, settings, focus, onExit = { leave(Screen.FRACTALS) })
                        Screen.TOOLS -> ToolsScreen(
                            schedule = schedule,
                            stopwatch = stopwatch,
                            use24h = settings.use24h,
                            onTimer = { if (schedule.timer != null) enter(Screen.TIMER) else screen = Screen.TIMER_SET },
                            onStopwatch = { enter(Screen.STOPWATCH) },
                            onAlarms = { screen = Screen.ALARMS },
                            onBack = { screen = Screen.HOME },
                        )
                        Screen.TIMER_SET -> TimerSetupScreen(
                            onStart = { ms ->
                                ensureNotifications()
                                schedule.startTimer(ms)
                                enter(Screen.TIMER)
                            },
                            onBack = { screen = Screen.TOOLS },
                        )
                        Screen.TIMER -> {
                            ImmersiveEffects(settings, focus, onExit = { leave(Screen.TOOLS) })
                            TimerScreen(settings, schedule, focus, onExit = { leave(Screen.TOOLS) })
                        }
                        Screen.STOPWATCH -> {
                            ImmersiveEffects(settings, focus, onExit = { leave(Screen.TOOLS) })
                            StopwatchScreen(settings, stopwatch, focus, onChange = { stopwatch = it }, onExit = { leave(Screen.TOOLS) })
                        }
                        Screen.ALARMS -> AlarmsScreen(
                            schedule = schedule,
                            use24h = settings.use24h,
                            onPickTone = ::chooseTone,
                            onSaved = ::ensureNotifications,
                            onBack = { screen = Screen.TOOLS },
                        )
                    }
                    RingState.banner?.let { ReminderBanner(it, settings.use24h, onSnooze = { schedule.snooze(it, it.snooze); RingState.banner = null }, onDismiss = { RingState.banner = null }) }
                    RingState.ringing?.let { RingingScreen(it, settings.use24h, onStop = { RingService.stop(this@MainActivity) }, onSnooze = { RingService.snooze(this@MainActivity) }) }
                }
            }
        }
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> permissionTick++
                // Focus time only counts while the clock, timer or stopwatch is on screen.
                Lifecycle.Event.ON_STOP -> { RingState.visible = false; if (immersive) log.end() }
                Lifecycle.Event.ON_START -> { RingState.visible = true; if (immersive) log.begin() }
                else -> Unit
            }
        })
        if (!settings.onboarded) pickFolder.launch(null)
    }

    private fun toggleNoise(kind: NoiseKind) {
        if (playing == kind) {
            noise.stop()
            playing = null
        } else {
            noise.play(kind)
            playing = kind
            lastNoise = kind
        }
    }

    private fun start() = enter(Screen.CLOCK)

    private fun stop() = leave(Screen.HOME)

    private fun enter(next: Screen) {
        val wasImmersive = immersive
        screen = next
        if (wasImmersive) return
        log.begin()
        if (settings.focus) focus.enter()
    }

    private fun leave(to: Screen) {
        log.end()
        focus.exit()
        screen = to
    }

    private fun ensureNotifications() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun chooseTone(current: String, result: (String) -> Unit) {
        toneResult = result
        pickTone.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current.takeIf { it.isNotEmpty() }?.let(Uri::parse)),
        )
    }

    /** An alarm that opens the app lights the screen and shows over the lock screen. */
    private fun showOverLock(i: Intent?) {
        val ring = i?.getBooleanExtra("ring", false) == true
        setShowWhenLocked(ring)
        setTurnScreenOn(ring)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showOverLock(intent)
    }

    override fun onDestroy() {
        log.end()
        noise.stop()
        focus.exit()
        super.onDestroy()
    }
}

/** Everything is sized for a 360dp-wide phone and scaled to the real screen. */
@Composable
internal fun scale(): Float {
    val c = LocalConfiguration.current
    return minOf(c.screenWidthDp, c.screenHeightDp) / 360f
}

/** Fades whatever is drawn toward transparent, so rows dim gradually as they near the edge of the screen. */
internal fun Modifier.fadeToBottom(from: Float) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Black, from to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun Home(playing: NoiseKind?, todayMinutes: Double, dailyTarget: Int, onStart: () -> Unit, onSettings: () -> Unit, onStats: () -> Unit, onTools: () -> Unit, onFractals: () -> Unit, onToggleNoise: (NoiseKind) -> Unit) {
    val u = scale()
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = (64 * u).dp)) {
            if (maxHeight > maxWidth) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height((28 * u).dp))
                    StartButton(u, todayMinutes, dailyTarget, onStart)
                    Spacer(Modifier.height((40 * u).dp))
                    NoiseList(u, playing, onToggleNoise, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StartButton(u, todayMinutes, dailyTarget, onStart) }
                    NoiseList(u, playing, onToggleNoise, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = (12 * u).dp),
            horizontalArrangement = Arrangement.spacedBy((14 * u).dp),
        ) {
            StatsIcon(u, onStats, Modifier)
            ToolsIcon(onTools, Modifier)
            FractalsIcon(onFractals, Modifier)
            Box(
                Modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onSettings).semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { GearIcon((39 * u).dp) }
        }
    }
}

/** Nine small squares in shades from dark to white, like the days grid of the statistics. */
@Composable
private fun StatsIcon(u: Float, onClick: () -> Unit, modifier: Modifier) {
    val shades = listOf(60, 120, 255, 180, 30, 90, 255, 150, 40)
    Box(
        modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Statistics" },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy((2.6 * u).dp)) {
            shades.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy((2.6 * u).dp)) {
                    row.forEach { g -> Box(Modifier.size((8.6 * u).dp).clip(RoundedCornerShape((2 * u).dp)).background(Color(g, g, g))) }
                }
            }
        }
    }
}

/**
 * Two equal rings with a gap between them. Today's focus time fills the whole band, rings and gap, from 3 o'clock
 * clockwise: coming back round to 3 o'clock is the daily target. Every lap after the first is a slightly greyer white.
 */
@Composable
private fun StartButton(u: Float, todayMinutes: Double, dailyTarget: Int, onClick: () -> Unit) {
    val laps = if (dailyTarget > 0) FocusStats.laps(todayMinutes / dailyTarget) else emptyList()
    Box(
        Modifier.size((232 * u).dp).clip(CircleShape).clickable(onClick = onClick).drawBehind {
            val ring = 4.5f * u * density
            val outer = 116f * u * density
            val inner = 98f * u * density
            val c = center
            drawCircle(Color.White, radius = outer - ring / 2, center = c, style = Stroke(ring))
            drawCircle(Color.White, radius = inner - ring / 2, center = c, style = Stroke(ring))
            // The band runs from the inner ring's inside edge to the outer ring's outside edge.
            val width = outer - (inner - ring)
            val r = outer - width / 2
            laps.forEachIndexed { k, sweep ->
                val g = FocusStats.lapGrey(k)
                drawArc(
                    Color(g, g, g), startAngle = 0f, sweepAngle = 360f * sweep, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = Stroke(width),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("Start", color = Color.White, fontSize = (40 * u).sp, fontWeight = FontWeight.Light, letterSpacing = (1.5 * u).sp)
        if (todayMinutes >= 1) {
            Text(
                FocusStats.duration(todayMinutes), color = Color.White, fontSize = (21 * u).sp, fontWeight = FontWeight.Light,
                modifier = Modifier.offset(y = (42 * u).dp),
            )
        }
    }
}

/**
 * The noises. Rows dim as they near the bottom edge, and the list can be scrolled until the last row sits where the first
 * one starts, in full brightness.
 */
@Composable
private fun NoiseList(u: Float, playing: NoiseKind?, onToggle: (NoiseKind) -> Unit, modifier: Modifier) {
    val rowHeight = (86 * u).dp
    BoxWithConstraints(modifier) {
        LazyColumn(Modifier.fillMaxSize().fadeToBottom(0.12f), contentPadding = PaddingValues(bottom = (maxHeight - rowHeight).coerceAtLeast(0.dp))) {
            items(NoiseKind.entries) { kind ->
                val on = playing == kind
                Row(
                    Modifier.fillMaxWidth().height(rowHeight).clickable { onToggle(kind) }.padding(horizontal = (46 * u).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayPause(on, (46 * u).dp) { onToggle(kind) }
                    Spacer(Modifier.width((26 * u).dp))
                    Text(kind.label, color = Color.White, fontSize = (29 * u).sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Light)
                }
            }
        }
    }
}

/** A round outline button: a play triangle, or pause bars while [playing]. */
@Composable
private fun PlayPause(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).border(size * 0.055f, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            Row(horizontalArrangement = Arrangement.spacedBy(size * 0.1f)) {
                repeat(2) { Box(Modifier.size(width = size * 0.12f, height = size * 0.38f).background(Color.White)) }
            }
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
    }
}

/** White-on-black switch: solid white with a black dot when on, an outline when off. */
@Composable
internal fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val u = scale()
    val x by animateDpAsState(if (checked) (26 * u).dp else 0.dp, label = "thumb")
    Box(
        Modifier.size(width = (62 * u).dp, height = (36 * u).dp).clip(CircleShape)
            .background(if (checked) Color.White else Color.Black)
            .border((2.5 * u).dp, Color.White, CircleShape)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = (7 * u).dp).offset(x = x).size((20 * u).dp).clip(CircleShape)
                .background(if (checked) Color.Black else Color.White)
        )
    }
}

/**
 * What the clock, timer and stopwatch screens share: the phone stays awake, the system bars are hidden, and if the user
 * leaves pinned mode with the system gesture the screen falls back out.
 */
@Composable
internal fun ImmersiveEffects(settings: ClockSettings, focus: Focus, onExit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController((view.context as ComponentActivity).window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val exit by rememberUpdatedState(onExit)
    LaunchedEffect(settings.focus) {
        if (!settings.focus) return@LaunchedEffect
        var wasPinned = false
        var missing = 0
        while (true) {
            delay(1_000)
            if (focus.pinned) { wasPinned = true; missing = 0 } else if (wasPinned && ++missing >= 2) { exit(); break }
        }
    }
}

@Composable
private fun ClockScreen(settings: ClockSettings, focus: Focus, playing: Boolean, onToggleSound: () -> Unit, onExit: () -> Unit, onBeat: () -> Unit) {
    val u = scale()
    val view = LocalView.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var hint by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }
    var touches by remember { mutableStateOf(0) }

    // Back does nothing: the only way out is to hold the time.
    BackHandler { }
    ImmersiveEffects(settings, focus, onExit)
    LaunchedEffect(settings.showSeconds) {
        val period = if (settings.showSeconds) 1_000L else 60_000L
        while (true) {
            now = LocalDateTime.now()
            delay(period - (System.currentTimeMillis() % period) + 20L)
        }
    }
    LaunchedEffect(Unit) { delay(4_000); hint = false }
    // Keeps the session's end current, so a killed app still loses at most half a minute.
    LaunchedEffect(Unit) { while (true) { delay(30_000); onBeat() } }
    // Controls appear on touch and fade away on their own.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        controls = true
        delay(3_000)
        controls = false
    }
    val h = "%02d".format(if (settings.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it })
    val m = "%02d".format(now.minute)
    val sec = "%02d".format(now.second)

    BoxWithConstraints(
        Modifier.fillMaxSize().systemBarsPadding().padding((12 * u).dp)
            .clickable { touches++ },
        contentAlignment = Alignment.Center,
    ) {
        val portrait = maxHeight > maxWidth
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val face = Modifier.fillMaxWidth().weight(1f).combinedClickable(onClick = { touches++ }, onLongClick = onExit)
            if (settings.style >= 0) {
                AnalogClock(AnalogStyle.entries[settings.style], now, settings.showSeconds, settings.use24h, ClockFonts[settings.fontIndex], face)
            } else {
                ClockFace(
                    if (portrait) listOfNotNull(h, m, sec.takeIf { settings.showSeconds }) else listOf(if (settings.showSeconds) "$h:$m:$sec" else "$h:$m"),
                    ClockFonts[settings.fontIndex],
                    face,
                )
            }
            if (settings.showDate) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    color = Color.White, fontSize = (26 * u).sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(vertical = (10 * u).dp),
                )
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = (14 * u).dp).alpha(if (controls) 1f else 0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((22 * u).dp),
        ) {
            Text("Focus mode", color = Color.White, fontSize = (22 * u).sp, fontWeight = FontWeight.Light)
            Toggle(settings.focus) {
                if (!controls) return@Toggle
                touches++
                settings.updateFocus(it)
                if (it) focus.enter() else focus.exit()
            }
            PlayPause(playing, (46 * u).dp) { if (controls) { touches++; onToggleSound() } }
        }
        Text(
            "Hold the time to exit",
            color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (6 * u).dp).alpha(if (hint) 1f else 0f),
        )
        PinHint(settings, focus, Modifier.align(Alignment.BottomCenter))
    }
}

/** Shown when focus mode is on but Android has not pinned the app, which usually means app pinning is off in its settings. */
@Composable
internal fun PinHint(settings: ClockSettings, focus: Focus, modifier: Modifier) {
    val u = scale()
    val context = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(settings.focus) {
        missing = false
        if (!settings.focus) return@LaunchedEffect
        delay(8_000)
        while (true) {
            missing = !focus.pinned
            delay(2_000)
        }
    }
    if (missing) {
        Text(
            "App pinning is off, so other apps can still open. Tap to open Security settings.",
            color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, textDecoration = TextDecoration.Underline,
            modifier = modifier.fillMaxWidth().clickable { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }.padding(horizontal = (24 * u).dp, vertical = (14 * u).dp),
        )
    }
}

/**
 * The time as big as it will go. Every digit sits in a cell as wide as the font's widest digit and every line is as
 * tall as the tallest digit, so "01" and "14" line up column for column, the block is centred, and nothing shifts
 * between minutes.
 */
@Composable
internal fun ClockFace(lines: List<String>, font: ClockFont, modifier: Modifier) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val paint = remember(font) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, font.res)
            textSize = 200f
        }
    }
    Canvas(modifier) {
        val ref = 200f
        val rowGap = 0.2f * ref
        fun adv(c: Char) = paint.measureText(c.toString())
        val digits = ('0'..'9').map { c -> Rect().also { paint.getTextBounds(c.toString(), 0, 1, it) } }
        val top = digits.minOf { it.top }.toFloat()
        val bottom = digits.maxOf { it.bottom }.toFloat()
        val digitCell = ('0'..'9').maxOf { adv(it) }
        val colonCell = adv(':')
        fun cell(c: Char) = if (c.isDigit()) digitCell else colonCell
        fun width(l: String) = l.sumOf { cell(it).toDouble() }.toFloat()
        val rowH = bottom - top
        val wRef = lines.maxOf { width(it) }
        val hRef = lines.size * rowH + (lines.size - 1) * rowGap
        val k = minOf(size.width * 0.94f / wRef, size.height * 0.96f / hRef)
        val style = TextStyle(color = Color.White, fontFamily = font.family, fontSize = (ref * k).toSp())
        var y = (size.height - hRef * k) / 2f
        lines.forEach { line ->
            var x = (size.width - width(line) * k) / 2f
            line.forEach { ch ->
                val layout = measurer.measure(ch.toString(), style)
                drawText(layout, topLeft = Offset(x + (cell(ch) - adv(ch)) / 2f * k, y - top * k - layout.firstBaseline))
                x += cell(ch) * k
            }
            y += (rowH + rowGap) * k
        }
    }
}

@Composable
private fun SettingsScreen(settings: ClockSettings, canSilence: Boolean, onGrantSilence: () -> Unit, onSpeakerChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    val top = (36 * u).dp
    val fontRow = (78 * u).dp
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        // The list can be scrolled until the last row of fonts sits where the first item starts, in full brightness.
        LazyVerticalGrid(
            GridCells.Adaptive((150 * u).dp),
            Modifier.fillMaxSize().fadeToBottom(0.72f),
            contentPadding = PaddingValues(start = (25 * u).dp, end = (25 * u).dp, top = top, bottom = (maxHeight - top - fontRow).coerceAtLeast(0.dp)),
            horizontalArrangement = Arrangement.spacedBy((12 * u).dp),
            verticalArrangement = Arrangement.spacedBy((12 * u).dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    ToggleRow("Focus mode", settings.focus, settings::updateFocus)
                    if (settings.focus && !canSilence) {
                        Text(
                            "Allow Do Not Disturb to silence notifications",
                            color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light, textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable(onClick = onGrantSilence).padding(vertical = (8 * u).dp),
                        )
                    }
                    ToggleRow("Show date", settings.showDate, settings::updateShowDate)
                    ToggleRow("24-hour time", settings.use24h, settings::updateUse24h)
                    ToggleRow("Show seconds", settings.showSeconds, settings::updateShowSeconds)
                    ToggleRow("Speaker with headphones", settings.noiseSpeaker, onSpeakerChange)
                    SectionTitle("App theme")
                    Segmented(AppTheme.entries.map { it.label }, settings.appTheme.ordinal) { settings.updateAppTheme(AppTheme.entries[it]) }
                    SectionTitle("Clock theme")
                    Segmented(ClockTheme.entries.map { it.label }, settings.clockTheme.ordinal) { settings.updateClockTheme(ClockTheme.entries[it]) }
                    SectionTitle("Focus targets")
                    listOf("Daily" to Period.Day, "Weekly" to Period.Week, "Monthly" to Period.Month, "Yearly" to Period.Year).forEach { (label, period) ->
                        TargetRow(label, settings.target(period), targetStep(period)) { settings.updateTarget(period, it) }
                    }
                    SectionTitle("Clock style")
                }
            }
            itemsIndexed(AnalogStyle.entries) { i, face ->
                val selected = i == settings.style
                Box(
                    Modifier.fillMaxWidth().height(fontRow)
                        .clip(RoundedCornerShape((18 * u).dp))
                        .border(((if (selected) 3.5 else 1.2) * u).dp, Color.White, RoundedCornerShape((18 * u).dp))
                        .clickable { settings.updateStyle(i) }
                        .semantics { contentDescription = face.label },
                    contentAlignment = Alignment.Center,
                ) {
                    AnalogClock(face, LocalDateTime.of(2026, 11, 2, 10, 42, 7), false, settings.use24h, ClockFonts[settings.fontIndex], Modifier.size(fontRow - (14 * u).dp))
                }
            }
            itemsIndexed(ClockFonts) { i, f ->
                val selected = settings.style < 0 && i == settings.fontIndex
                Box(
                    Modifier.fillMaxWidth().height(fontRow)
                        .clip(RoundedCornerShape((18 * u).dp))
                        .border(((if (selected) 3.5 else 1.2) * u).dp, Color.White, RoundedCornerShape((18 * u).dp))
                        .clickable { settings.chooseFont(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        if (settings.showSeconds) "10:42:07" else "10:42", maxLines = 1,
                        style = TextStyle(color = Color.White, fontFamily = f.family, textAlign = TextAlign.Center),
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = (40 * u).sp),
                        modifier = Modifier.padding(horizontal = (10 * u).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val u = scale()
    Text(text, color = Color.White, fontSize = (25 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = (26 * u).dp, bottom = (8 * u).dp))
}

/** A row of equal choices; the chosen one is solid white. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val u = scale()
    Row(Modifier.fillMaxWidth().padding(bottom = (4 * u).dp), horizontalArrangement = Arrangement.spacedBy((8 * u).dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.Black)
                    .border((2 * u).dp, Color.White, RoundedCornerShape(50)).clickable { onSelect(i) }.padding(vertical = (10 * u).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.Black else Color.White, fontSize = (19 * u).sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Light, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val u = scale()
    Row(Modifier.fillMaxWidth().padding(vertical = (20 * u).dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = (25 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        Toggle(value, onChange)
    }
}

/** A target with a minus and a plus: it steps by [step] minutes and reads "Off" at zero. */
@Composable
private fun TargetRow(label: String, minutes: Int, step: Int, onChange: (Int) -> Unit) {
    val u = scale()
    Row(Modifier.fillMaxWidth().padding(vertical = (20 * u).dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = (25 * u).sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        StepButton(plus = false) { onChange(minutes - step) }
        Text(
            FocusStats.target(minutes), color = Color.White, fontSize = (25 * u).sp, fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.width((96 * u).dp),
        )
        StepButton(plus = true) { onChange(minutes + step) }
    }
}

@Composable
internal fun StepButton(plus: Boolean, onClick: () -> Unit) {
    val u = scale()
    Box(
        Modifier.size((40 * u).dp).clip(CircleShape).clickable(onClick = onClick).border((2.5 * u).dp, Color.White, CircleShape)
            .semantics { contentDescription = if (plus) "Increase" else "Decrease" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = (16 * u).dp, height = (2.5 * u).dp).background(Color.White))
        if (plus) Box(Modifier.size(width = (2.5 * u).dp, height = (16 * u).dp).background(Color.White))
    }
}
