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

/** Plays [signal] through the splicer for [passes] passes, as the player would, and returns what it plays. */
private fun streamed(signal: ShortArray, channels: Int, crossfade: Int, estimate: Long, passes: Int): ShortArray {
    val splicer = LoopSplicer(channels, crossfade, estimate)
    val played = ArrayList<Short>()
    val emit = { d: ShortArray, off: Int, len: Int -> for (i in off until off + len) played.add(d[i]) }
    var skipFirst = 0
    repeat(passes) {
        splicer.beginPass()
        // The decoder hands over chunks of an awkward size.
        var pos = 0
        while (pos < signal.size) {
            val n = minOf(1_000 * channels + 6, signal.size - pos)
            splicer.feed(signal.copyOfRange(pos, pos + n), emit)
            pos += n
        }
        splicer.endPass(emit)
        skipFirst++
    }
    return played.toShortArray()
}

class LoopSplicerTest {
    private val channels = 2
    private val crossfade = 4_000
    private val frames = 30_000
    private val signal = ShortArray(frames * channels) { Random(5).let { r -> (it * 37 % 20_000 - 10_000).toShort() } }

    private fun expected(passes: Int): ShortArray {
        val loop = Recordings.seamless(Pcm(48_000, channels, signal), crossfade / 48_000.0)
        val prefix = signal.copyOfRange(0, (frames - crossfade) * channels)
        var out = prefix
        repeat(passes) { out += loop }
        // The last pass ends part way through the loop, in the fade back to its start.
        return out + loop.copyOfRange(0, crossfade * channels)
    }

    @Test fun theStreamIsTheRecordingFollowedByTheSeamlessLoopOverAndOver() {
        // Pass one plays the plain recording up to the fade; every pass after that is the loop.
        val got = streamed(signal, channels, crossfade, frames.toLong(), 4)
        val want = expected(3)
        assertEquals(want.size, got.size)
        for (i in want.indices) if (want[i] != got[i]) throw AssertionError("differs at sample $i: ${got[i]} instead of ${want[i]}")
    }

    @Test fun anEstimateOfTheLengthThatIsOffByAFewHundredMillisecondsStillLoopsCleanly() {
        for (off in listOf(-9_000L, -500L, 700L, 6_000L)) {
            val got = streamed(signal, channels, crossfade, frames + off, 3)
            // From the second pass on, the loop repeats exactly: find one period and check the next matches it.
            val period = (frames - crossfade) * channels
            val tailStart = got.size - 2 * period
            assertTrue("estimate off by $off: too short", tailStart >= 0)
            for (i in 0 until period) {
                if (got[tailStart + i] != got[tailStart + period + i]) throw AssertionError("estimate off by $off: loop differs at $i")
            }
        }
    }

    @Test fun theFadeIntoTheLoopIsContinuousWithTheSoundBeforeIt() {
        val got = streamed(signal, channels, crossfade, frames.toLong(), 2)
        val join = (frames - crossfade) * channels
        // The first sample of the fade is the last sample before it, so there is no jump.
        assertEquals(signal[join].toInt(), got[join].toInt())
    }
}
