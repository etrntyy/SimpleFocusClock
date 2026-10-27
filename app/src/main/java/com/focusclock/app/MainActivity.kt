package com.focusclock.app

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen { HOME, CLOCK, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var settings: ClockSettings
    private lateinit var focus: Focus
    private val noise = NoisePlayer()
    private var screen by mutableStateOf(Screen.HOME)
    private var permissionTick by mutableStateOf(0)
    private var playing by mutableStateOf<NoiseKind?>(null)
    private var lastNoise = NoiseKind.White

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = ClockSettings(this)
        focus = Focus(this)
        // The noise plays on the alarm stream so alarms-only Do Not Disturb doesn't mute it.
        volumeControlStream = AudioManager.STREAM_ALARM
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (screen) {
                        Screen.HOME -> Home(
                            playing = playing,
                            onStart = ::start,
                            onSettings = { screen = Screen.SETTINGS },
                            onToggleNoise = ::toggleNoise,
                        )
                        Screen.CLOCK -> ClockScreen(
                            settings = settings,
                            focus = focus,
                            playing = playing != null,
                            onToggleSound = { toggleNoise(playing ?: lastNoise) },
                            onExit = ::stop,
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            canSilence = permissionTick >= 0 && focus.canSilence,
                            onGrantSilence = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                            onBack = { screen = Screen.HOME },
                        )
                    }
                }
            }
        }
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) permissionTick++
        })
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

    private fun start() {
        screen = Screen.CLOCK
        if (settings.focus) focus.enter()
    }

    private fun stop() {
        focus.exit()
        screen = Screen.HOME
    }

    override fun onDestroy() {
        noise.stop()
        focus.exit()
        super.onDestroy()
    }
}

/** Everything is sized for a 360dp-wide phone and scaled to the real screen. */
@Composable
private fun scale(): Float {
    val c = LocalConfiguration.current
    return minOf(c.screenWidthDp, c.screenHeightDp) / 360f
}

/** Fades whatever is drawn toward transparent, so rows dim gradually as they near the edge of the screen. */
private fun Modifier.fadeToBottom(from: Float) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Black, from to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun Home(playing: NoiseKind?, onStart: () -> Unit, onSettings: () -> Unit, onToggleNoise: (NoiseKind) -> Unit) {
    val u = scale()
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = (64 * u).dp)) {
            if (maxHeight > maxWidth) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height((28 * u).dp))
                    StartButton(u, onStart)
                    Spacer(Modifier.height((40 * u).dp))
                    NoiseList(u, playing, onToggleNoise, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StartButton(u, onStart) }
                    NoiseList(u, playing, onToggleNoise, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Text(
            "Simple Focus Clock", color = Color.White, fontSize = (21 * u).sp, fontWeight = FontWeight.Light, letterSpacing = (1.5 * u).sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = (28 * u).dp, top = (22 * u).dp),
        )
        Icon(
            Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding((12 * u).dp).size((52 * u).dp)
                .clip(CircleShape).clickable(onClick = onSettings).padding((10 * u).dp),
        )
    }
}

@Composable
private fun StartButton(u: Float, onClick: () -> Unit) {
    Box(Modifier.size((232 * u).dp).border((1.5 * u).dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((196 * u).dp).clip(CircleShape).clickable(onClick = onClick).border((4.5 * u).dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("Start", color = Color.White, fontSize = (40 * u).sp, fontWeight = FontWeight.Light, letterSpacing = (1.5 * u).sp) }
    }
}

/** The noises. The list runs to the bottom edge of the screen and each row dims as it gets closer to it. */
@Composable
private fun NoiseList(u: Float, playing: NoiseKind?, onToggle: (NoiseKind) -> Unit, modifier: Modifier) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier.fadeToBottom(0.12f), contentPadding = PaddingValues(bottom = navBottom + (60 * u).dp)) {
        items(NoiseKind.entries) { kind ->
            val on = playing == kind
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(kind) }.padding(horizontal = (46 * u).dp, vertical = (20 * u).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPause(on, (46 * u).dp) { onToggle(kind) }
                Spacer(Modifier.width((26 * u).dp))
                Text(kind.label, color = Color.White, fontSize = (29 * u).sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Light)
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
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
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

@Composable
private fun ClockScreen(settings: ClockSettings, focus: Focus, playing: Boolean, onToggleSound: () -> Unit, onExit: () -> Unit) {
    val u = scale()
    val view = LocalView.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var hint by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }
    var touches by remember { mutableStateOf(0) }

    BackHandler(onBack = onExit)

    // The phone never sleeps while the clock is open.
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
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 20L)
        }
    }
    LaunchedEffect(Unit) { delay(4_000); hint = false }
    // Controls appear on touch and fade away on their own.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        controls = true
        delay(3_000)
        controls = false
    }
    // If the user leaves pinned mode with the system gesture, fall back to the home screen.
    LaunchedEffect(settings.focus) {
        if (!settings.focus) return@LaunchedEffect
        var wasPinned = false
        var missing = 0
        while (true) {
            delay(1_000)
            if (focus.pinned) { wasPinned = true; missing = 0 } else if (wasPinned && ++missing >= 2) { onExit(); break }
        }
    }

    val h = "%02d".format(if (settings.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it })
    val m = "%02d".format(now.minute)

    BoxWithConstraints(
        Modifier.fillMaxSize().systemBarsPadding().padding((12 * u).dp)
            .combinedClickable(onClick = { touches++ }, onLongClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        val portrait = maxHeight > maxWidth
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ClockFace(if (portrait) listOf(h, m) else listOf("$h:$m"), ClockFonts[settings.fontIndex], Modifier.fillMaxWidth().weight(1f))
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
            Icon(
                Icons.Filled.Close, contentDescription = "Exit", tint = Color.White,
                modifier = Modifier.size((48 * u).dp).clip(CircleShape).clickable { if (controls) onExit() }.padding((8 * u).dp),
            )
        }
        Text(
            "Hold the time to exit",
            color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (6 * u).dp).alpha(if (hint) 1f else 0f),
        )
    }
}

/**
 * The time as big as it will go. Every digit sits in a cell as wide as the font's widest digit and every line is as
 * tall as the tallest digit, so "01" and "14" line up column for column, the block is centred, and nothing shifts
 * between minutes.
 */
@Composable
private fun ClockFace(lines: List<String>, font: ClockFont, modifier: Modifier) {
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
private fun SettingsScreen(settings: ClockSettings, canSilence: Boolean, onGrantSilence: () -> Unit, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        GridCells.Adaptive((150 * u).dp),
        Modifier.fillMaxSize().statusBarsPadding().fadeToBottom(0.72f),
        contentPadding = PaddingValues(start = (25 * u).dp, end = (25 * u).dp, top = (36 * u).dp, bottom = navBottom + (80 * u).dp),
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
                Text(
                    "Font", color = Color.White, fontSize = (25 * u).sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = (26 * u).dp, bottom = (8 * u).dp),
                )
            }
        }
        itemsIndexed(ClockFonts) { i, f ->
            val selected = i == settings.fontIndex
            Box(
                Modifier.fillMaxWidth().height((78 * u).dp)
                    .clip(RoundedCornerShape((18 * u).dp))
                    .border(((if (selected) 3.5 else 1.2) * u).dp, Color.White, RoundedCornerShape((18 * u).dp))
                    .clickable { settings.updateFontIndex(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "10:42", maxLines = 1,
                    style = TextStyle(color = Color.White, fontFamily = f.family, textAlign = TextAlign.Center),
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = (40 * u).sp),
                    modifier = Modifier.padding(horizontal = (10 * u).dp),
                )
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
