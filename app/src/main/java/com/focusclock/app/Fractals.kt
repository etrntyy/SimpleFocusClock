package com.focusclock.app

import android.graphics.Bitmap
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The four fractals that are drawn as lines and dots. They are white on black, so the light theme flips them. */
enum class LineFractal(val label: String, val blurb: String) {
    Hilbert("Hilbert curve", "One line that fills the square"),
    Tree("Fractal tree", "Grows, then sways in the wind"),
    Sierpinski("Sierpinski triangle", "Built from random dots"),
    Dragon("Dragon curve", "A folded line that twists"),
}

/** What the Fractals section is showing: every shader in turn, one shader, or one of the line fractals. */
sealed interface FractalChoice {
    data object Shuffle : FractalChoice
    data class OfShader(val id: String) : FractalChoice
    data class OfLine(val kind: LineFractal) : FractalChoice

    /** Whether it is drawn by the GPU, in its own surface, rather than by the app's own drawing. */
    val onGpu get() = this !is OfLine
}

/** The Hilbert curve as an icon, drawn in the same thin white line as the hourglass. */
@Composable
internal fun HilbertIcon(size: Dp, modifier: Modifier = Modifier) {
    val u = scale()
    val points = remember { FractalMath.hilbert(3) }
    Canvas(modifier.size(size)) {
        val pad = 2f * u * density
        val span = this.size.width - 2 * pad
        val path = Path()
        for (i in 0 until points.size / 2) {
            val x = pad + points[2 * i] * span
            val y = pad + points[2 * i + 1] * span
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White, style = Stroke(2.6f * u * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The icon on the home screen that opens the fractals. */
@Composable
internal fun FractalsIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val u = scale()
    Box(
        modifier.size((52 * u).dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Fractals" },
        contentAlignment = Alignment.Center,
    ) { HilbertIcon((35 * u).dp) }
}

@Composable
internal fun FractalsScreen(mono: Boolean, onMono: (Boolean) -> Unit, onPick: (FractalChoice) -> Unit, onBack: () -> Unit) {
    val u = scale()
    BackHandler(onBack = onBack)
    @Composable
    fun row(title: String, blurb: String, choice: FractalChoice) {
        Row(
            Modifier.fillMaxWidth().clickable { onPick(choice) }.padding(horizontal = (28 * u).dp, vertical = (20 * u).dp)
                .drawBehind { drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), 1.2f * u * density) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = (27 * u).sp, fontWeight = FontWeight.Light)
                Text(blurb, color = Color.White.copy(alpha = 0.6f), fontSize = (16 * u).sp, fontWeight = FontWeight.Light)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size((32 * u).dp))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Fractals", onBack)
        Spacer(Modifier.height((16 * u).dp))
        Box(Modifier.padding(horizontal = (28 * u).dp)) {
            Segmented(listOf("Colour", "Black and white"), if (mono) 1 else 0) { onMono(it == 1) }
        }
        Spacer(Modifier.height((10 * u).dp))
        row("Shuffle", "Every fractal in turn, a minute each", FractalChoice.Shuffle)
        ShaderCatalog.shaders.forEach { row(it.name, it.blurb, FractalChoice.OfShader(it.id)) }
        LineFractal.entries.forEach { row(it.label, it.blurb + ". Always black and white", FractalChoice.OfLine(it)) }
        Spacer(Modifier.height((40 * u).dp))
    }
}

/** One fractal on the whole screen, like the clock: pinned in focus mode, and held down anywhere to leave. */
@Composable
internal fun FractalScreen(choice: FractalChoice, mono: Boolean, light: Boolean, settings: ClockSettings, focus: Focus, onExit: () -> Unit) {
    val u = scale()
    BackHandler { }
    ImmersiveEffects(settings, focus, onExit)
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(4_000); hint = false }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (choice) {
            is FractalChoice.OfLine -> LineView(choice.kind, onExit)
            else -> ShaderView(
                RenderConfig(
                    shaderId = (choice as? FractalChoice.OfShader)?.id ?: ShaderCatalog.shaders.first().id,
                    palette = if (mono) ShaderCatalog.MONO else -1,
                    cycleMinutes = if (choice is FractalChoice.Shuffle) 1 else 0,
                    invert = mono && light,
                ),
                onExit,
            )
        }
        Text(
            "Hold anywhere to leave", color = Color.White, fontSize = (17 * u).sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (10 * u).dp).alpha(if (hint) 1f else 0f),
        )
        PinHint(settings, focus, Modifier.align(Alignment.BottomCenter))
    }
}

/** A fragment shader drawn on the GPU into its own surface, which can be a fraction of the screen's size to save power. */
@Composable
private fun ShaderView(config: RenderConfig, onExit: () -> Unit) {
    val context = LocalContext.current
    val renderer = remember { ShaderRenderer(context.assets) }
    val driver = remember { SurfaceDriver(renderer) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> renderer.setVisible(true)
                Lifecycle.Event.ON_STOP -> renderer.setVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        renderer.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            renderer.release()
        }
    }
    LaunchedEffect(config) {
        driver.scale = config.scale
        renderer.setConfig(config)
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun uv(p: Offset): Offset {
        val short = min(size.width, size.height).toFloat()
        if (short <= 0f) return Offset.Zero
        return Offset((2f * p.x - size.width) / short, (size.height - 2f * p.y) / short)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) = driver.created(h)
                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) = driver.changed(h, width, height)
                        override fun surfaceDestroyed(h: SurfaceHolder) = driver.destroyed()
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // A transparent layer on top takes the touches: a tap nudges the fractal, a drag steers it, a long press leaves.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { val q = uv(it); renderer.tap(q.x, q.y) }, onLongPress = { onExit() }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { val q = uv(it); renderer.touch(q.x, q.y, true) },
                        onDrag = { change, _ -> val q = uv(change.position); renderer.touch(q.x, q.y, true) },
                        onDragEnd = { renderer.touch(0f, 0f, false) },
                        onDragCancel = { renderer.touch(0f, 0f, false) },
                    )
                },
        )
    }
}

/** The fractals drawn as lines and dots by the app itself. */
@Composable
private fun LineView(kind: LineFractal, onExit: () -> Unit) {
    var t by remember(kind) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(kind) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9 }
    }
    val hilbert = remember { FractalMath.hilbert(5) }
    val turns = remember { FractalMath.dragonTurns(11) }
    val chaos = remember(kind) { Chaos() }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onExit() }) }) {
        val frameTime = t
        when (kind) {
            LineFractal.Hilbert -> drawHilbert(hilbert, frameTime)
            LineFractal.Tree -> drawTree(frameTime)
            LineFractal.Sierpinski -> drawIntoCanvas { chaos.advance(size.width.toInt(), size.height.toInt(), it.nativeCanvas) }
            LineFractal.Dragon -> drawDragon(turns, frameTime)
        }
    }
}

private fun DrawScope.drawHilbert(points: FloatArray, t: Double) {
    val n = points.size / 2
    val side = min(size.width, size.height) * 0.9f
    val ox = (size.width - side) / 2f
    val oy = (size.height - side) / 2f
    val cycle = 26.0
    val u = ((t % cycle) / cycle).toFloat()
    // A head draws the line, then the tail follows it off the screen.
    val head = (min(1f, u / 0.45f) * (n - 1)).toInt()
    val tail = (((u - 0.5f) / 0.45f).coerceIn(0f, 1f) * (n - 1)).toInt()
    val path = Path()
    for (i in tail..head) {
        val x = ox + points[2 * i] * side
        val y = oy + points[2 * i + 1] * side
        if (i == tail) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color.White, style = Stroke(max(2f, side / 170f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawTree(t: Double) {
    val grow = (t / 14.0).coerceIn(0.0, 1.0).toFloat() * 9.6f
    val trunk = min(size.height * 0.2f, size.width * 0.38f)
    val base = min(size.width, size.height) / 55f
    fun branch(x: Float, y: Float, angle: Float, length: Float, level: Int, width: Float) {
        if (level >= 10 || grow <= level) return
        val f = (grow - level).coerceIn(0f, 1f)
        val x2 = x + length * f * sin(angle)
        val y2 = y - length * f * cos(angle)
        drawLine(Color.White, Offset(x, y), Offset(x2, y2), max(1.5f, width), StrokeCap.Round)
        val spread = 0.42f + 0.07f * sin(t * 0.6).toFloat()
        val flutter = 0.035f * sin(t * 1.3 + level).toFloat()
        branch(x2, y2, angle - spread + flutter, length * 0.74f, level + 1, width * 0.74f)
        branch(x2, y2, angle + spread + flutter, length * 0.74f, level + 1, width * 0.74f)
    }
    branch(size.width / 2f, size.height * 0.93f, 0f, trunk, 0, base)
}

/** The chaos game: jump half way to a random corner, again and again, and the triangle appears. */
private class Chaos {
    private var bitmap: Bitmap? = null
    private var canvas: android.graphics.Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 235; strokeWidth = 2.4f }
    private val random = Random(1)
    private var x = 0.3f
    private var y = 0.3f
    private var count = 0

    fun advance(width: Int, height: Int, target: android.graphics.Canvas) {
        if (width < 2 || height < 2) return
        var bmp = bitmap
        if (bmp == null || bmp.width != width || bmp.height != height) {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            canvas = android.graphics.Canvas(bmp)
            count = 0
        }
        if (count > 220_000) {
            bmp.eraseColor(android.graphics.Color.TRANSPARENT)
            count = 0
        }
        val side = min(width, height) * 0.9f
        val left = (width - side) / 2f
        val top = (height - side * 0.866f) / 2f
        val cornersX = floatArrayOf(left + side / 2f, left, left + side)
        val cornersY = floatArrayOf(top, top + side * 0.866f, top + side * 0.866f)
        val c = canvas ?: return
        repeat(140) {
            val i = random.nextInt(3)
            x = (x + cornersX[i]) / 2f
            y = (y + cornersY[i]) / 2f
            c.drawPoint(x, y, paint)
            count++
        }
        target.drawBitmap(bmp, 0f, 0f, null)
    }
}

private fun DrawScope.drawDragon(turns: IntArray, t: Double) {
    val angle = PI / 2 + 0.34 * sin(t * 0.2)
    val p = FractalMath.dragon(turns, angle)
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (i in 0 until p.size / 2) {
        minX = min(minX, p[2 * i]); maxX = max(maxX, p[2 * i])
        minY = min(minY, p[2 * i + 1]); maxY = max(maxY, p[2 * i + 1])
    }
    val k = min(size.width * 0.88f / max(0.001f, maxX - minX), size.height * 0.88f / max(0.001f, maxY - minY))
    val ox = (size.width - (maxX - minX) * k) / 2f - minX * k
    val oy = (size.height - (maxY - minY) * k) / 2f - minY * k
    val path = Path()
    for (i in 0 until p.size / 2) {
        val x = p[2 * i] * k + ox
        val y = p[2 * i + 1] * k + oy
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color.White, style = Stroke(max(1.5f, min(size.width, size.height) / 260f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
