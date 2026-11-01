package com.focusclock.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal object Dsp {
    /** In-place radix-2 FFT. [sign] is -1 for forward, +1 for inverse (unscaled). */
    fun fft(re: DoubleArray, im: DoubleArray, sign: Int) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = sign * 2.0 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun aMag(f: Double): Double {
        val f2 = f * f
        return 12194.0 * 12194.0 * f2 * f2 /
            ((f2 + 20.6 * 20.6) * sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) * (f2 + 12194.0 * 12194.0))
    }

    private val aRef = aMag(1000.0)

    /** Power gain of the A-weighting curve (ear sensitivity), 1.0 at 1 kHz. */
    fun aPower(f: Double): Double { val a = aMag(f) / aRef; return a * a }

    /** Target loudness: A-weighted RMS of every sound, and a ceiling on the plain RMS so peaks stay below clipping. */
    const val TARGET_A_RMS = 0.075
    const val MAX_RMS = 0.2

    /**
     * A looping noise whose spectrum is exactly [mag] (amplitude per Hz) between 20 Hz and 20 kHz, built by giving
     * every frequency bin that amplitude and a random phase. Because it is built in the frequency domain it repeats
     * seamlessly, and its slope is exact rather than approximated by a filter.
     */
    fun spectral(n: Int, rate: Int, rng: Random, mag: (Double) -> Double): FloatArray {
        val re = DoubleArray(n); val im = DoubleArray(n)
        var sumSq = 0.0; var aSumSq = 0.0
        for (k in 1 until n / 2) {
            val f = k.toDouble() * rate / n
            if (f < 20.0 || f > 20_000.0) continue
            val m = mag(f)
            val ph = rng.nextDouble() * 2 * PI
            re[k] = m * cos(ph); im[k] = m * sin(ph)
            re[n - k] = re[k]; im[n - k] = -im[k]
            sumSq += m * m; aSumSq += m * m * aPower(f)
        }
        fft(re, im, +1)
        // With an unscaled inverse FFT, Parseval gives mean-square = the sum of bin powers over all N bins.
        val rms = sqrt(2.0 * sumSq)
        val aRms = sqrt(2.0 * aSumSq)
        val scale = minOf(TARGET_A_RMS / aRms, MAX_RMS / rms)
        return FloatArray(n) { (re[it] * scale).toFloat() }
    }
}
