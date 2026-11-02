package com.focusclock.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderCatalogTest {
    private val shaders = File("src/main/assets/shaders")

    @Test
    fun everyFractalHasAShaderThatDefinesTheEntryPoint() {
        for (f in ShaderCatalog.shaders) {
            val file = File(shaders, f.file)
            assertTrue("missing ${f.file}", file.exists())
            assertTrue("${f.file} has no fractal()", file.readText().contains("vec3 fractal(vec2 fc)"))
        }
    }

    @Test
    fun idsAreUniqueAndDefaultPalettesExist() {
        assertEquals(ShaderCatalog.shaders.size, ShaderCatalog.shaders.map { it.id }.toSet().size)
        for (f in ShaderCatalog.shaders) assertTrue(f.defaultPalette in ShaderCatalog.palettes.indices)
    }

    @Test
    fun theSharedPartsFlipColoursForTheLightTheme() {
        assertTrue(File(shaders, "common.glsl").readText().contains("uniform float uInvert"))
        assertTrue(File(shaders, "footer.glsl").readText().contains("uInvert"))
    }

    @Test
    fun theMonoPaletteIsGreyAtEveryPointAndOpaque() {
        val mono = ShaderCatalog.palettes[ShaderCatalog.MONO]
        for (i in 0..8) {
            val c = mono.colorAt(i / 8f)
            assertEquals(0xFF, c ushr 24)
            assertEquals((c shr 16) and 0xFF, (c shr 8) and 0xFF)
            assertEquals((c shr 8) and 0xFF, c and 0xFF)
        }
    }

    @Test
    fun thereArePlentyOfFractals() {
        assertTrue(ShaderCatalog.shaders.size >= 35)
        assertEquals(4, LineFractal.entries.size)
    }
}
