package com.focusclock.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

private val invertPaint = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

/**
 * Draws everything below it with every colour flipped, which is how the light theme works: the app is designed in white
 * on black, so flipping turns white into true black, black into white, and the faintest dark grey into a faint off-white.
 */
fun Modifier.inverted(on: Boolean): Modifier = if (!on) this else drawWithContent {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), invertPaint)
        drawContent()
        canvas.restore()
    }
}
