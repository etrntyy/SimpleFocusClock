package com.focusclock.app

import kotlin.math.cos

data class Shader(
    val id: String,
    val name: String,
    val blurb: String,
    val file: String,
    val defaultPalette: Int,
)

data class Palette(val name: String, val a: FloatArray, val b: FloatArray, val c: FloatArray, val d: FloatArray) {
    /** Same cosine palette the shaders use, for drawing swatches. Returns packed ARGB. */
    fun colorAt(t: Float): Int {
        fun ch(i: Int): Int {
            val v = a[i] + b[i] * cos(6.28318f * (c[i] * t + d[i]))
            return (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }
}

object ShaderCatalog {
    val shaders = listOf(
        Shader("seahorse", "Seahorse Dive", "An endless zoom into the Mandelbrot set's Seahorse Valley.", "seahorse.frag", 0),
        Shader("julia", "Julia Drift", "A Julia set whose seed wanders along the edge of the Mandelbrot set. Tap to steer it.", "julia.frag", 4),
        Shader("ship", "Burning Ship", "Flying in toward the mast of the Burning Ship fractal.", "ship.frag", 5),
        Shader("tricorn", "Tricorn", "The three-horned Mandelbar set, rotating and breathing.", "tricorn.frag", 2),
        Shader("newton", "Nova Basins", "Newton's method with a relaxation that breathes, so the basins slowly melt into each other.", "newton.frag", 3),
        Shader("multibrot", "Multibrot Morph", "The Mandelbrot set slowly turning into its cubic cousin and back.", "multibrot.frag", 0),
        Shader("kali", "Kali Lace", "Invert, fold, repeat: organic filigree from the Kali set.", "kifs.frag", 3),
        Shader("apollonian", "Apollonian Bubbles", "Circle inversion packs ever smaller bubbles into the gaps.", "apollonian.frag", 4),
        Shader("nebula", "Fractal Nebula", "Warped fractal noise, drifting like gas clouds.", "nebula.frag", 2),
        Shader("bulb", "Mandelbulb", "A raymarched 3D Mandelbulb that changes its power as the camera circles. The heaviest one on the GPU.", "bulb.frag", 1),
        Shader("carpet", "Carpet Zoom", "The Sierpinski carpet, zooming forever into its own corner.", "carpet.frag", 3),
        Shader("crystal", "Crystal Julia", "A Julia set painted with orbit traps instead of escape counts.", "crystal.frag", 6),
        Shader("phoenix", "Phoenix", "Each step remembers the one before it, which gives flame-like feathers. Tap to nudge it.", "phoenix.frag", 1),
        Shader("neon", "Neon Mandelbrot", "The Mandelbrot set as glowing contour lines, drifting into the triple spiral valley.", "neon.frag", 3),
        Shader("mandelbox", "Mandelbox", "A slice of the Mandelbox: fold the plane like paper, inflate, repeat.", "mandelbox.frag", 7),
        Shader("collatz", "Complex Collatz", "The Collatz conjecture continued onto the complex plane, with bulbs growing from the real axis.", "collatz.frag", 2),
        Shader("triangle", "Sierpinski Zoom", "The Sierpinski gasket zooming forever, because every corner holds the whole pattern again.", "triangle.frag", 0),
        Shader("menger", "Menger Sponge", "A raymarched Menger sponge turning slowly, with holes inside holes.", "menger.frag", 9),
        Shader("tetra", "Sierpinski Pyramid", "A raymarched Sierpinski tetrahedron spinning through the dark.", "tetra.frag", 8),
        Shader("celtic", "Celtic Knots", "The Mandelbrot set with its real part folded, so the edge ties itself in knots.", "celtic.frag", 9),
        Shader("perpship", "Perpendicular Ship", "A Burning Ship folded the other way, so the fleet sails sideways.", "perpship.frag", 5),
        Shader("magnet", "Magnet Julia", "Two ways to escape, one to infinity and one to a single point, with a seed that drifts. Tap to steer it.", "magnet.frag", 2),
        Shader("sine", "Sine Spirals", "Iterating c times sin z: tongues and spirals that repeat along a line. Tap to steer it.", "sine.frag", 4),
        Shader("expo", "Exponential River", "Iterating c times e to the z: a river of little spirals.", "expo.frag", 6),
        Shader("flower", "Julia Flowers", "Julia sets for a seed circling the Mandelbrot set: flowers that open and close. Tap to steer it.", "flower.frag", 8),
        Shader("stalks", "Orbit Stalks", "A Julia set painted by how close each orbit gets to the axes: glowing stalks and tendrils. Tap to steer it.", "stalks.frag", 3),
        Shader("glass", "Stained Glass", "The Mandelbrot set through stained glass, coloured by the circle each orbit came closest to.", "glass.frag", 0),
        Shader("newton5", "Quintic Basins", "Newton's method for five roots, with basins that melt into each other. Tap to nudge it.", "newton5.frag", 1),
        Shader("elephant", "Elephant Valley", "A long dive into the far side of the Mandelbrot set.", "elephant.frag", 7),
        Shader("snowflake", "Snowflake Folds", "Fold into a sixth, shrink, shift, repeat: a snowflake whose arms grow arms.", "snowflake.frag", 6),
        Shader("lightning", "Lightning", "The Julia set for c equal to i: a fine web of dendrites with no inside.", "lightning.frag", 4),
        Shader("biomorph", "Biomorphs", "Cells and microbes from a test on the axes. Tap to nudge it.", "biomorph.frag", 7),
        Shader("vicsek", "Vicsek Cross", "The Vicsek cross zooming forever: every plus sign is made of five smaller ones.", "vicsek.frag", 9),
        Shader("quartic", "Quartic Lace", "Julia sets of z to the fourth plus c: four-fold lace as the seed circles. Tap to steer it.", "quartic.frag", 3),
        Shader("inversion", "Inside-out Mandelbrot", "The Mandelbrot set turned inside out, with infinity in the middle.", "inversion.frag", 2),
    )

    val palettes = listOf(
        Palette("Rainbow", f(.5f, .5f, .5f), f(.5f, .5f, .5f), f(1f, 1f, 1f), f(0f, .33f, .67f)),
        Palette("Ember", f(.5f, .5f, .5f), f(.5f, .5f, .5f), f(1f, 1f, .5f), f(.8f, .9f, .3f)),
        Palette("Ocean", f(0f, .5f, .5f), f(0f, .5f, .5f), f(0f, .5f, .333f), f(0f, .5f, .667f)),
        Palette("Neon", f(.5f, .5f, .5f), f(.5f, .5f, .5f), f(2f, 1f, 0f), f(.5f, .2f, .25f)),
        Palette("Aurora", f(.5f, .5f, .5f), f(.5f, .5f, .5f), f(1f, .7f, .4f), f(0f, .15f, .2f)),
        Palette("Magma", f(.55f, .25f, .2f), f(.5f, .3f, .2f), f(1f, 1f, 1f), f(0f, .1f, .2f)),
        Palette("Ice", f(.6f, .7f, .9f), f(.3f, .3f, .3f), f(1f, 1f, 1f), f(0f, .1f, .25f)),
        Palette("Forest", f(.3f, .5f, .3f), f(.3f, .4f, .3f), f(1f, 1f, .8f), f(.2f, 0f, .3f)),
        Palette("Candy", f(.6f, .5f, .6f), f(.4f, .4f, .4f), f(1f, 1f, 1f), f(0f, .4f, .7f)),
        Palette("Gold", f(.5f, .4f, .2f), f(.5f, .4f, .2f), f(1f, 1f, 1f), f(0f, .1f, .2f)),
        // Equal in every channel, so it only ever makes greys: the black and white versions.
        Palette("Mono", f(.5f, .5f, .5f), f(.5f, .5f, .5f), f(1f, 1f, 1f), f(0f, 0f, 0f)),
    )

    /** The palette index that turns any fractal black and white. */
    val MONO = palettes.lastIndex

    private fun f(a: Float, b: Float, c: Float) = floatArrayOf(a, b, c)

    fun byId(id: String): Shader = shaders.firstOrNull { it.id == id } ?: shaders.first()

    fun indexOf(id: String): Int = shaders.indexOfFirst { it.id == id }.coerceAtLeast(0)

    fun paletteFor(shader: Shader, choice: Int): Palette =
        palettes[if (choice in palettes.indices) choice else shader.defaultPalette]
}
