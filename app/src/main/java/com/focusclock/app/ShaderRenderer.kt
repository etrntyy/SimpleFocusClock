package com.focusclock.app

import android.content.res.AssetManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one fractal fragment shader full-screen on its own GL thread.
 * Every public method is safe to call from any thread; the GL work happens on the renderer's thread.
 */
class ShaderRenderer(private val assets: AssetManager) {
    private val thread = HandlerThread("fractal-gl").also { it.start() }
    private val handler = Handler(thread.looper)

    // Everything below is touched only on the GL thread.
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surface: Surface? = null
    private var visible = false
    private var config = RenderConfig()
    private var program = 0
    private var shownId: String? = null
    private var autoId: String? = null
    private var timeSec = 0f
    private var lastNanos = 0L
    private var fade = 0f
    private var cycleClock = 0f
    private var scroll = 0f
    private var scrollTarget = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var touchGoalX = 0f
    private var touchGoalY = 0f
    private var touchAmt = 0f
    private var touchDown = false
    private val sources = HashMap<String, String>()
    private val uniforms = HashMap<String, Int>()
    private val vertices = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        position(0)
    }
    private val frame = Runnable { drawFrame() }

    fun setSurface(s: Surface?) {
        if (s != null) {
            handler.post { createSurface(s) }
            return
        }
        // The caller's surface is invalid once this returns, so wait until we have let go of it.
        val done = CountDownLatch(1)
        if (handler.post { destroyAll(); done.countDown() }) done.await(1, TimeUnit.SECONDS)
    }

    fun setVisible(v: Boolean) {
        handler.post {
            visible = v
            lastNanos = 0L
            if (v) startLoop() else handler.removeCallbacks(frame)
        }
    }

    fun setConfig(c: RenderConfig) {
        handler.post {
            if (c.shaderId != config.shaderId || c.cycleMinutes == 0) {
                autoId = null
                cycleClock = 0f
            }
            config = c
            if (!c.parallax) scrollTarget = 0f
        }
    }

    /** Launcher page position as -1 (first page) to 1 (last page). */
    fun setScroll(x: Float) {
        handler.post { scrollTarget = if (config.parallax) x.coerceIn(-1f, 1f) else 0f }
    }

    /** Pointer position in the shader's uv space; [down] false lets the effect fade away. */
    fun touch(x: Float, y: Float, down: Boolean) {
        handler.post {
            touchGoalX = x
            touchGoalY = y
            if (down && !touchDown) {
                touchX = x
                touchY = y
            }
            touchDown = down
        }
    }

    fun tap(x: Float, y: Float) {
        handler.post {
            touchX = x
            touchY = y
            touchGoalX = x
            touchGoalY = y
            touchDown = false
            touchAmt = 1f
        }
    }

    fun release() {
        val done = CountDownLatch(1)
        if (handler.post { destroyAll(); done.countDown() }) done.await(1, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    private fun createSurface(s: Surface) {
        surface = s
        if (display == EGL14.EGL_NO_DISPLAY && !initEgl()) return
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
        }
        eglSurface = EGL14.eglCreateWindowSurface(display, eglConfig, s, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)
        ) {
            Log.e(TAG, "Could not bind the window surface: 0x${Integer.toHexString(EGL14.eglGetError())}")
            destroyAll()
            return
        }
        EGL14.eglSwapInterval(display, 1)
        startLoop()
    }

    private fun initEgl(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
            Log.e(TAG, "EGL is not available")
            display = EGL14.EGL_NO_DISPLAY
            return false
        }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 0, EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            Log.e(TAG, "No suitable EGL config")
            destroyAll()
            return false
        }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            display, eglConfig, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "Could not create a GLES2 context")
            destroyAll()
            return false
        }
        return true
    }

    private fun destroyAll() {
        handler.removeCallbacks(frame)
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        eglConfig = null
        surface = null
        // The program died with the context.
        program = 0
        shownId = null
        uniforms.clear()
    }

    private fun startLoop() {
        handler.removeCallbacks(frame)
        if (visible && eglSurface != EGL14.EGL_NO_SURFACE) handler.post(frame)
    }

    private fun drawFrame() {
        if (!visible || eglSurface == EGL14.EGL_NO_SURFACE) return
        val started = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((started - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = started
        timeSec += dt * config.speed
        advanceState(dt)

        val size = IntArray(1)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, size, 0)
        val w = size[0]
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, size, 0)
        val h = size[0]
        GLES20.glViewport(0, 0, w, h)
        if (program != 0) {
            GLES20.glUseProgram(program)
            setUniforms(w, h)
            vertices.position(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        if (!EGL14.eglSwapBuffers(display, eglSurface)) {
            val err = EGL14.eglGetError()
            Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(err)}")
            if (err == EGL14.EGL_CONTEXT_LOST) {
                val s = surface
                destroyAll()
                if (s != null) createSurface(s)
            }
            // Any other failure means the surface is gone; surfaceDestroyed will follow.
            return
        }
        val budgetMs = 1000L / config.fps.coerceIn(10, 120)
        val spentMs = (System.nanoTime() - started) / 1_000_000L
        handler.postDelayed(frame, max(0L, budgetMs - spentMs - 2))
    }

    private fun advanceState(dt: Float) {
        if (config.cycleMinutes > 0 && shownId != null) {
            cycleClock += dt
            if (cycleClock >= config.cycleMinutes * 60f) {
                cycleClock = 0f
                val all = ShaderCatalog.shaders
                autoId = all[(ShaderCatalog.indexOf(shownId!!) + 1) % all.size].id
            }
        }
        val wanted = autoId ?: config.shaderId
        if (wanted != shownId) {
            if (shownId == null || fade <= 0f) {
                loadProgram(wanted)
                fade = 0f
            } else {
                fade = max(0f, fade - dt / 0.4f)
            }
        } else {
            fade = min(1f, fade + dt / 0.7f)
        }

        scroll += (scrollTarget - scroll) * min(1f, dt * 4f)
        touchX += (touchGoalX - touchX) * min(1f, dt * 8f)
        touchY += (touchGoalY - touchY) * min(1f, dt * 8f)
        touchAmt = if (touchDown) min(1f, touchAmt + dt * 4f) else max(0f, touchAmt - dt * 0.35f)
    }

    private fun setUniforms(w: Int, h: Int) {
        val shader = ShaderCatalog.byId(shownId ?: config.shaderId)
        val pal = ShaderCatalog.paletteFor(shader, config.palette)
        GLES20.glUniform2f(loc("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(loc("uTime"), timeSec)
        GLES20.glUniform1f(loc("uScroll"), scroll)
        GLES20.glUniform2f(loc("uTouch"), touchX, touchY)
        GLES20.glUniform1f(loc("uTouchAmt"), touchAmt)
        GLES20.glUniform1f(loc("uFade"), fade * fade * (3f - 2f * fade))
        GLES20.glUniform1f(loc("uInvert"), if (config.invert) 1f else 0f)
        GLES20.glUniform3fv(loc("uPalA"), 1, pal.a, 0)
        GLES20.glUniform3fv(loc("uPalB"), 1, pal.b, 0)
        GLES20.glUniform3fv(loc("uPalC"), 1, pal.c, 0)
        GLES20.glUniform3fv(loc("uPalD"), 1, pal.d, 0)
    }

    private fun loc(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(program, name) }

    private fun loadProgram(id: String) {
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        uniforms.clear()
        shownId = id
        timeSec = 0f
        cycleClock = 0f
        try {
            val fragment = asset("common.glsl") + asset(ShaderCatalog.byId(id).file) + asset("footer.glsl")
            program = link(compile(GLES20.GL_VERTEX_SHADER, VERTEX), compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        } catch (e: Exception) {
            Log.e(TAG, "Could not build $id", e)
        }
    }

    private fun asset(name: String): String = sources.getOrPut(name) {
        assets.open("shaders/$name").bufferedReader().use { it.readText() }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("shader compile failed: $log")
        }
        return shader
    }

    private fun link(vertex: Int, fragment: Int): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vertex)
        GLES20.glAttachShader(p, fragment)
        GLES20.glBindAttribLocation(p, 0, "aPos")
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw IllegalStateException("program link failed: $log")
        }
        return p
    }

    private companion object {
        const val TAG = "ShaderRenderer"
        const val VERTEX = "attribute vec2 aPos;\nvoid main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n"
    }
}

/** What the renderer needs to know. */
data class RenderConfig(
    val shaderId: String = "seahorse",
    /** Index into [ShaderCatalog.palettes], or -1 for the fractal's own palette. */
    val palette: Int = -1,
    val speed: Float = 1f,
    val fps: Int = 30,
    /** Share of the screen's resolution the shader renders at. */
    val scale: Float = 0.75f,
    /** Minutes between automatic fractal changes; 0 means stay on one. */
    val cycleMinutes: Int = 0,
    val parallax: Boolean = false,
    /** Flip every colour, for the light theme. */
    val invert: Boolean = false,
)
