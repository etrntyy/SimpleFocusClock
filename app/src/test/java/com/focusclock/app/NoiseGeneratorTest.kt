package com.focusclock.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

class NoiseGeneratorTest {
    private val rate = 44_100

    private fun samples(kind: NoiseKind, seconds: Int = 20): FloatArray {
        val g = NoiseGenerator(kind, rate, Random(7))
        return FloatArray(rate * seconds) { g.next() }
    }

    /** Welch power spectrum: mean periodogram over Hann-windowed 8192-sample blocks. */
    private fun spectrum(x: FloatArray): DoubleArray {
        val n = 8192
        val acc = DoubleArray(n / 2)
        var blocks = 0
        var s = 0
        while (s + n <= x.size) {
            val re = DoubleArray(n) { x[s + it] * (0.5 - 0.5 * Math.cos(2 * Math.PI * it / n)) }
            val im = DoubleArray(n)
            Dsp.fft(re, im, -1)
            for (k in 0 until n / 2) acc[k] += re[k] * re[k] + im[k] * im[k]
            blocks++; s += n / 2
        }
        return DoubleArray(n / 2) { acc[it] / blocks }
    }

    private fun db(p: Double) = 10 * log10(p)

    /** Power spectral density (dB per Hz, relative) averaged over the octave centred on [fc]. */
    private fun band(spec: DoubleArray, fc: Double): Double {
        val n = spec.size * 2
        var sum = 0.0; var count = 0
        for (k in 1 until spec.size) {
            val f = k.toDouble() * rate / n
            if (f >= fc / 1.4142 && f < fc * 1.4142) { sum += spec[k]; count++ }
        }
        return db(sum / count)
    }

    private val centres = doubleArrayOf(63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    /** Slope in dB per octave between 100 Hz and 10 kHz, from a least-squares line through the spectrum. */
    private fun tilt(spec: DoubleArray): Double {
        val n = spec.size * 2
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        var k = 1
        while (k < spec.size) {
            val f = k.toDouble() * rate / n
            if (f in 100.0..10_000.0) { xs.add(Math.log(f) / Math.log(2.0)); ys.add(db(spec[k])) }
            k++
        }
        val mx = xs.average(); val my = ys.average()
        var num = 0.0; var den = 0.0
        for (i in xs.indices) { num += (xs[i] - mx) * (ys[i] - my); den += (xs[i] - mx) * (xs[i] - mx) }
        return num / den
    }

    private fun aRms(x: FloatArray): Double {
        val sp = spectrum(x)
        val n = sp.size * 2
        var s = 0.0
        for (k in 1 until sp.size) s += sp[k] * Dsp.aPower(k.toDouble() * rate / n)
        var all = 0.0
        for (k in 1 until sp.size) all += sp[k]
        return sqrt(s / all) * rms(x)
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    @Test fun coloursHaveTheirTextbookSlopes() {
        val want = mapOf(NoiseKind.White to 0.0, NoiseKind.Pink to -3.0, NoiseKind.Brown to -6.0, NoiseKind.Blue to 3.0, NoiseKind.Violet to 6.0)
        for ((k, slope) in want) {
            val t = tilt(spectrum(samples(k)))
            assertTrue("$k slope $t, want $slope", abs(t - slope) < 0.4)
        }
    }

    @Test fun greyIsTheInverseOfTheEarsSensitivity() {
        val sp = spectrum(samples(NoiseKind.Grey))
        val rel = centres.associateWith { band(sp, it) - band(sp, 1000.0) }
        println("GREY " + rel)
        assertTrue("63 Hz ${rel[63.0]}", rel[63.0]!! > 12)
        assertTrue("250 Hz ${rel[250.0]}", rel[250.0]!! in 5.0..12.0)
        assertTrue("4 kHz ${rel[4000.0]}", rel[4000.0]!! in -3.0..1.5)
        assertTrue("16 kHz ${rel[16000.0]}", rel[16000.0]!! > 3)
    }

    @Test fun greenSitsInTheMidRange() {
        val sp = spectrum(samples(NoiseKind.Green))
        val peak = centres.maxByOrNull { band(sp, it) }!!
        assertTrue("peak at $peak", peak in 250.0..1000.0)
        assertTrue(band(sp, 500.0) - band(sp, 63.0) > 12)
        assertTrue(band(sp, 500.0) - band(sp, 8000.0) > 12)
    }

    @Test fun everySoundIsAboutAsLoudToTheEarAndDoesNotClip() {
        val problems = ArrayList<String>()
        for (k in NoiseKind.entries) {
            val x = samples(k)
            val a = aRms(x)
            val clipped = x.count { abs(it) > 1f } / x.size.toDouble()
            println("LOUD $k aRms=${"%.3f".format(a)} rms=${"%.3f".format(rms(x))} clipped=$clipped")
            // Brown is held below the others: its energy is so low in pitch that matching them would clip.
            if (a !in (if (k == NoiseKind.Brown) 0.04 else 0.06)..0.1) problems.add("$k A-weighted level $a")
            if (clipped > 0.001) problems.add("$k clipped $clipped")
        }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    /** Shape of the spectrum (9 octave levels, mean removed, in 6 dB units) plus how bursty and how slowly it swells. */
    private fun features(k: NoiseKind): DoubleArray {
        val x = samples(k, 40)
        val sp = spectrum(x)
        val lv = centres.map { band(sp, it) }
        val m = lv.average()
        val shape = lv.map { (it - m) / 6.0 }
        // Loudness over time in 100 ms steps: how much it varies, and how much of that is slow (over 3 s) swelling.
        val env = DoubleArray(x.size / 4410) { i -> var s = 0.0; for (j in 0 until 4410) { val v = x[i * 4410 + j].toDouble(); s += v * v }; sqrt(s / 4410) }
        val mean = env.average()
        val variation = sqrt(env.sumOf { (it - mean) * (it - mean) } / env.size) / mean
        val slow = DoubleArray(env.size) { i -> var s = 0.0; var c = 0; for (j in maxOf(0, i - 15)..minOf(env.size - 1, i + 15)) { s += env[j]; c++ }; s / c }
        val sm = slow.average()
        val slowVar = sqrt(slow.sumOf { (it - sm) * (it - sm) } / slow.size) / sm
        var k4 = 0.0; var v2 = 0.0
        for (v in x) { v2 += v.toDouble() * v }
        v2 /= x.size
        for (v in x) { val d = v.toDouble(); k4 += d * d * d * d }
        val kurt = k4 / x.size / (v2 * v2)
        return (shape + listOf(variation * 4, slowVar * 4, Math.log10(kurt) * 3)).toDoubleArray()
    }

    @Test fun noTwoSoundsAreTooAlike() {
        val all = NoiseKind.entries.associateWith { features(it) }
        val problems = ArrayList<String>()
        val kinds = NoiseKind.entries
        for (i in kinds.indices) for (j in i + 1 until kinds.size) {
            val a = all.getValue(kinds[i]); val b = all.getValue(kinds[j])
            val d = sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })
            println("DIST ${kinds[i]} ${kinds[j]} ${"%.2f".format(d)}")
            if (d < 0.9) problems.add("${kinds[i]} vs ${kinds[j]} ${"%.2f".format(d)}")
        }
        for (k in kinds) println("FEAT $k " + all.getValue(k).joinToString(" ") { "%.2f".format(it) })
        assertTrue("too alike: " + problems.joinToString("; "), problems.isEmpty())
    }
}
