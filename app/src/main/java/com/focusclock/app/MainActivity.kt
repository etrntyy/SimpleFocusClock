package com.focusclock.app

import android.content.Intent
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

@Composable
private fun Home(playing: NoiseKind?, onStart: () -> Unit, onSettings: () -> Unit, onToggleNoise: (NoiseKind) -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 56.dp)) {
            if (maxHeight > maxWidth) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    StartButton(onStart)
                    Spacer(Modifier.height(28.dp))
                    NoiseList(playing, onToggleNoise, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StartButton(onStart) }
                    NoiseList(playing, onToggleNoise, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Text(
            "Simple Focus Clock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp),
        )
        Icon(
            Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp).clip(CircleShape).clickable(onClick = onSettings).padding(10.dp),
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Box(Modifier.size(156.dp).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(132.dp).clip(CircleShape).clickable(onClick = onClick).border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("Start", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp) }
    }
}

/** The noises, fading out toward the bottom of the screen. */
@Composable
private fun NoiseList(playing: NoiseKind?, onToggle: (NoiseKind) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.25f to Color.Black, 0.97f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        items(NoiseKind.entries) { kind ->
            val on = playing == kind
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(kind) }.padding(horizontal = 40.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPause(on, 30.dp) { onToggle(kind) }
                Spacer(Modifier.width(16.dp))
                Text(kind.label, color = Color.White, fontSize = 18.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Light)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** A round outline button: a play triangle, or pause bars while [playing]. */
@Composable
private fun PlayPause(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).border(1.5.dp, Color.White, CircleShape),
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
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, label = "thumb")
    Box(
        Modifier.size(width = 38.dp, height = 22.dp).clip(CircleShape)
            .background(if (checked) Color.White else Color.Black)
            .border(1.5.dp, Color.White, CircleShape)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(start = 4.dp).offset(x = x).size(12.dp).clip(CircleShape).background(if (checked) Color.Black else Color.White))
    }
}

@Composable
private fun ClockScreen(settings: ClockSettings, focus: Focus, playing: Boolean, onToggleSound: () -> Unit, onExit: () -> Unit) {
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

    val h = if (settings.use24h) "%02d".format(now.hour) else (now.hour % 12).let { if (it == 0) 12 else it }.toString()
    val m = "%02d".format(now.minute)
    val style = TextStyle(color = Color.White, fontFamily = ClockFonts[settings.fontIndex].family, textAlign = TextAlign.Center)

    BoxWithConstraints(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
            .combinedClickable(onClick = { touches++ }, onLongClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        val portrait = maxHeight > maxWidth
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (portrait) {
                Fit(h, style, Modifier.fillMaxWidth().weight(1f))
                Fit(m, style, Modifier.fillMaxWidth().weight(1f))
            } else {
                Fit("$h:$m", style, Modifier.fillMaxWidth().weight(1f))
            }
            if (settings.showDate) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).alpha(if (controls) 1f else 0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Focus mode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Light)
            Toggle(settings.focus) {
                if (!controls) return@Toggle
                touches++
                settings.updateFocus(it)
                if (it) focus.enter() else focus.exit()
            }
            PlayPause(playing, 32.dp) { if (controls) { touches++; onToggleSound() } }
            Icon(
                Icons.Filled.Close, contentDescription = "Exit", tint = Color.White,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { if (controls) onExit() }.padding(6.dp),
            )
        }
        Text(
            "Hold the time to exit",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.BottomCenter).alpha(if (hint) 1f else 0f),
        )
    }
}

/** One line of text scaled to fill its box. */
@Composable
private fun Fit(text: String, style: TextStyle, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(
            text, style = style, maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 600.sp, stepSize = 2.sp),
        )
    }
}

@Composable
private fun SettingsScreen(settings: ClockSettings, canSilence: Boolean, onGrantSilence: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyVerticalGrid(
        GridCells.Adaptive(130.dp),
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 20.dp)) {
                ToggleRow("Focus mode", settings.focus, settings::updateFocus)
                if (settings.focus && !canSilence) {
                    Text(
                        "Allow Do Not Disturb to silence notifications",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Light, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onGrantSilence).padding(vertical = 6.dp),
                    )
                }
                ToggleRow("Show date", settings.showDate, settings::updateShowDate)
                ToggleRow("24-hour time", settings.use24h, settings::updateUse24h)
                Text("Font", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
        }
        itemsIndexed(ClockFonts) { i, f ->
            val selected = i == settings.fontIndex
            Box(
                Modifier.fillMaxWidth().height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.45f), RoundedCornerShape(12.dp))
                    .clickable { settings.updateFontIndex(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "10:42", maxLines = 1,
                    style = TextStyle(color = Color.White.copy(alpha = if (selected) 1f else 0.6f), fontFamily = f.family, textAlign = TextAlign.Center),
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 34.sp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        Toggle(value, onChange)
    }
}
