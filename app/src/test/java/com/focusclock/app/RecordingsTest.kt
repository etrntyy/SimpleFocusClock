package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

class RecordingsTest {
    private val kinds = NoiseKind.entries.filter { Recordings.assetFor(it) != null }

    /** Reads the Ogg container directly: the Opus header, and the length from the last page's position. */
    private fun inspect(f: File): Triple<Int, Int, Double> {
        val b = f.readBytes()
        val head = String(b, 0, minOf(b.size, 400), Charsets.ISO_8859_1).indexOf("OpusHead")
        assertTrue("${f.name}: not an Opus file", head >= 0)
        val channels = b[head + 9].toInt() and 0xff
        val preSkip = (b[head + 10].toInt() and 0xff) or ((b[head + 11].toInt() and 0xff) shl 8)
        var last = -1
        for (i in b.size - 4 downTo 0) if (b[i] == 'O'.code.toByte() && b[i + 1] == 'g'.code.toByte() && b[i + 2] == 'g'.code.toByte() && b[i + 3] == 'S'.code.toByte()) { last = i; break }
        var granule = 0L
        for (k in 7 downTo 0) granule = (granule shl 8) or (b[last + 6 + k].toLong() and 0xff)
        return Triple(channels, preSkip, (granule - preSkip) / 48000.0)
    }

    @Test fun everyNaturalSoundHasAStereoOpusRecordingOfUsableLength() {
        for (k in kinds) {
            val f = File("src/main/assets/${Recordings.assetFor(k)}")
            assertTrue("$k: ${f.path} missing", f.exists())
            val (channels, _, seconds) = inspect(f)
            println("REC $k ${f.name} channels=$channels seconds=${"%.1f".format(seconds)} bytes=${f.length()}")
            assertEquals("$k channels", 2, channels)
            assertTrue("$k only $seconds s long", seconds >= 25.0)
        }
    }

    @Test fun loopJoinIsSeamlessAndKeepsTheLevelEven() {
        val rate = 48_000
        val rng = Random(3)
        val frames = rate * 20
        val pcm = Pcm(rate, 2, ShortArray(frames * 2) { (rng.nextInt(-9000, 9000)).toShort() })
        val out = Recordings.seamless(pcm, 3.0)
        val outFrames = out.size / 2
        assertEquals("loses exactly the crossfade length", frames - 3 * rate, outFrames)
        fun rms(from: Int, to: Int): Double { var s = 0.0; for (i in from until to) s += out[i].toDouble() * out[i]; return sqrt(s / (to - from)) }
        val body = rms(10 * rate * 2, 12 * rate * 2)
        // Level in short windows through the crossfade stays within 1.5 dB of the rest of the sound.
        for (w in 0 until 3 * rate step rate / 4) {
            val level = rms(w * 2, (w + rate / 4) * 2)
            val dB = 20 * log10(level / body)
            assertTrue("level through the join drifts by $dB dB at $w", abs(dB) < 1.5)
        }
        // The wrap from the last sample back to the first is no bigger than a step inside the noise.
        val wrapStep = abs(out[out.size - 2].toInt() - out[0].toInt())
        assertTrue("wrap step $wrapStep", wrapStep < 18_001)
    }

    @Test fun soundsRequiringCreditAreCreditedInTheReadme() {
        val readme = File("../README.md").readText()
        for (name in listOf("Glaneur de sons", "Gravity Sound")) assertTrue("README should credit $name", readme.contains(name))
    }
}
