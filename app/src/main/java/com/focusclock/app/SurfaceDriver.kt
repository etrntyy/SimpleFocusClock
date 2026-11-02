package com.focusclock.app

import android.view.SurfaceHolder
import kotlin.math.max

/**
 * Connects a [SurfaceHolder] to a [ShaderRenderer] and renders at a fraction of the screen's resolution
 * by giving the surface a smaller fixed size; the compositor scales it back up.
 */
class SurfaceDriver(private val renderer: ShaderRenderer) {
    private var holder: SurfaceHolder? = null
    private var fixedW = 0
    private var fixedH = 0

    /** Size of the surface as the system laid it out, before any downscaling. */
    var fullW = 0
        private set
    var fullH = 0
        private set

    var scale = 1f
        set(value) {
            if (field == value) return
            field = value
            holder?.let { resize(it) }
        }

    fun created(h: SurfaceHolder) {
        holder = h
    }

    fun changed(h: SurfaceHolder, w: Int, height: Int) {
        holder = h
        // A size we did not ask for is a genuine change in layout.
        if (w != fixedW || height != fixedH) {
            fullW = w
            fullH = height
        }
        if (!resize(h)) renderer.setSurface(h.surface)
    }

    fun destroyed() {
        holder = null
        fixedW = 0
        fixedH = 0
        renderer.setSurface(null)
    }

    /** Returns true when it asked the system for another size, which will come back through [changed]. */
    private fun resize(h: SurfaceHolder): Boolean {
        if (fullW == 0 || fullH == 0) return false
        val tw = max(1, (fullW * scale).toInt())
        val th = max(1, (fullH * scale).toInt())
        if (tw == fixedW && th == fixedH) return false
        fixedW = tw
        fixedH = th
        h.setFixedSize(tw, th)
        return true
    }
}
