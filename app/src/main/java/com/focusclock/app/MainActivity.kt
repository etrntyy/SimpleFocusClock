package com.focusclock.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private var screen by mutableStateOf(Screen.HOME)
    private var permissionTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = ClockSettings(this)
        focus = Focus(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (screen) {
                        Screen.HOME -> Home(
                            onStart = ::start,
                            onSettings = { screen = Screen.SETTINGS },
                        )
                        Screen.CLOCK -> ClockScreen(settings, focus, onExit = ::stop)
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

    private fun start() {
        screen = Screen.CLOCK
        if (settings.focusOnStart) focus.enter()
    }

    private fun stop() {
        focus.exit()
        screen = Screen.HOME
    }

    override fun onDestroy() {
        focus.exit()
        super.onDestroy()
    }
}

@Composable
private fun Home(onStart: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(180.dp).border(3.dp, Color.White, CircleShape).clip(CircleShape).clickable(onClick = onStart),
            contentAlignment = Alignment.Center,
        ) { Text("Start", color = Color.White, fontSize = 30.sp) }
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onSettings) { Text("Settings", color = Color(0xFF9A9A9A)) }
    }
}

@Composable
private fun ClockScreen(settings: ClockSettings, focus: Focus, onExit: () -> Unit) {
    val view = LocalView.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var hint by remember { mutableStateOf(true) }
    var focusOn by remember { mutableStateOf(settings.focusOnStart) }
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
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    LaunchedEffect(focusOn) {
        if (!focusOn) return@LaunchedEffect
        var wasPinned = false
        var missing = 0
        while (true) {
            delay(1_000)
            if (focus.pinned) { wasPinned = true; missing = 0 } else if (wasPinned && ++missing >= 2) { onExit(); break }
        }
    }

    val h = if (settings.use24h) "%02d".format(now.hour) else (now.hour % 12).let { if (it == 0) 12 else it }.toString()
    val m = "%02d".format(now.minute)
    val family = ClockFonts[settings.fontIndex].family
    val style = TextStyle(color = Color.White, fontFamily = family, textAlign = TextAlign.Center)

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
                    color = Color(0xFF8A8A8A), fontSize = 18.sp, fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).alpha(if (controls) 1f else 0f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Focus mode", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = focusOn,
                onCheckedChange = {
                    if (!controls) return@Switch
                    touches++
                    focusOn = it
                    if (it) focus.enter() else focus.exit()
                },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { if (controls) onExit() }) { Text("Exit", color = Color(0xFFCCCCCC)) }
        }
        Text(
            "Hold the time to exit",
            color = Color(0xFF666666), fontSize = 13.sp,
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
            Column {
                TextButton(onClick = onBack) { Text("Back", color = Color(0xFF9A9A9A)) }
                SwitchRow("Focus mode on Start", settings.focusOnStart, settings::updateFocusOnStart)
                if (settings.focusOnStart && !canSilence) {
                    TextButton(onClick = onGrantSilence) { Text("Allow Do Not Disturb to silence notifications") }
                }
                SwitchRow("Show date", settings.showDate, settings::updateShowDate)
                SwitchRow("24-hour time", settings.use24h, settings::updateUse24h)
                Text("Font", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
        }
        itemsIndexed(ClockFonts) { i, f ->
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.9f)
                    .border(if (i == settings.fontIndex) 2.dp else 0.5.dp, if (i == settings.fontIndex) Color.White else Color(0xFF444444), RoundedCornerShape(10.dp))
                    .clickable { settings.updateFontIndex(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "10:42", maxLines = 1,
                    style = TextStyle(color = Color.White, fontFamily = f.family, textAlign = TextAlign.Center),
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 40.sp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
