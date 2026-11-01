package com.focusclock.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The noises on offer: the coloured ones are defined by their spectrum, the rest imitate natural sounds. */
enum class NoiseKind(val label: String, val coloured: Boolean = false) {
    White("White noise", true),
    Pink("Pink noise", true),
    Brown("Brown noise", true),
    Blue("Blue noise", true),
    Violet("Violet noise", true),
    Grey("Grey noise", true),
    Green("Green noise", true),
    Rain("Rain"),
    Thunder("Thunderstorm"),
    Ocean("Ocean waves"),
    Wind("Wind"),
    Stream("Stream"),
    Fire("Campfire"),
    Fan("Fan"),
}

/**
 * Plays a noise through an [AudioTrack]. Alarm usage keeps it audible under alarms-only Do Not Disturb. The natural
 * sounds are real recordings; if one can't be opened, the generated version of that sound plays instead.
 */
class NoisePlayer(private val context: Context) {
    private var running: AtomicBoolean? = null

    fun play(kind: NoiseKind) {
        stop()
        val flag = AtomicBoolean(true)
        running = flag
        Thread({ run(kind, flag) }, "noise").start()
    }

    fun stop() {
        running?.set(false)
        running = null
    }

    private fun track(rate: Int, channels: Int): AudioTrack {
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(mask).build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(rate / 2 * 2 * channels)
            .build()
    }

    private fun run(kind: NoiseKind, alive: AtomicBoolean) {
        val recording = Recordings.assetFor(kind)?.let { path -> runCatching { Recordings.load(context, path) }.getOrNull() }
        if (!alive.get()) return
        if (recording != null) loop(recording, alive) else generate(kind, alive)
    }

    private fun loop(pcm: Pcm, alive: AtomicBoolean) {
        val track = track(pcm.sampleRate, pcm.channels)
        track.play()
        try {
            var pos = 0
            val chunk = 4096 * pcm.channels
            while (alive.get()) {
                val n = minOf(chunk, pcm.data.size - pos)
                track.write(pcm.data, pos, n)
                pos += n
                if (pos >= pcm.data.size) pos = 0
            }
        } finally {
            track.stop(); track.release()
        }
    }

    private fun generate(kind: NoiseKind, alive: AtomicBoolean) {
        val rate = 44_100
        val gen = NoiseGenerator(kind, rate)
        if (!alive.get()) return
        val frames = ShortArray(2048 * 2)
        val pair = FloatArray(2)
        val track = track(rate, 2)
        track.play()
        try {
            while (alive.get()) {
                for (i in 0 until 2048) {
                    gen.nextPair(pair)
                    frames[2 * i] = (pair[0].coerceIn(-1f, 1f) * 32767f * 0.9f).toInt().toShort()
                    frames[2 * i + 1] = (pair[1].coerceIn(-1f, 1f) * 32767f * 0.9f).toInt().toShort()
                }
                track.write(frames, 0, frames.size)
            }
        } finally {
            track.stop(); track.release()
        }
    }
}

private class OnePole(private var a: Float) {
    var y = 0f
    fun set(a: Float) { this.a = a }
    fun process(x: Float): Float { y += a * (x - y); return y }
}

/** A pool of short decaying sounds: raindrop pings, water bubbles, fire crackles. */
private class Voices(private val rate: Int, private val rng: Random) {
    private val n = 32
    private val amp = FloatArray(n)
    private val phase = DoubleArray(n)
    private val step = DoubleArray(n)
    private val chirp = DoubleArray(n) { 1.0 }
    private val chirpLeft = IntArray(n)
    private val decay = FloatArray(n)
    private val click = FloatArray(n)
    private val clickDecay = FloatArray(n)
    private var slot = 0

    fun spawn(a: Float, freq: Double, tau: Double, clickLevel: Float = 0f, clickTau: Double = 0.0006, rise: Double = 1.0, riseSec: Double = 0.01) {
        val i = slot; slot = (slot + 1) % n
        amp[i] = a; phase[i] = 0.0
        step[i] = 2.0 * PI * freq / rate
        chirpLeft[i] = (riseSec * rate).toInt()
        chirp[i] = if (rise == 1.0) 1.0 else rise.pow(1.0 / (riseSec * rate))
        decay[i] = exp(-1.0 / (tau * rate)).toFloat()
        click[i] = clickLevel
        clickDecay[i] = exp(-1.0 / (clickTau * rate)).toFloat()
    }

    fun next(): Float {
        var s = 0f
        for (i in 0 until n) {
            val a = amp[i]; val c = click[i]
            if (a < 1e-4f && c < 1e-4f) continue
            if (a >= 1e-4f) {
                s += a * sin(phase[i]).toFloat()
                phase[i] += step[i]
                if (chirpLeft[i] > 0) { step[i] *= chirp[i]; chirpLeft[i]-- }
                amp[i] = a * decay[i]
            }
            if (c >= 1e-4f) {
                s += c * (rng.nextFloat() * 2f - 1f)
                click[i] = c * clickDecay[i]
            }
        }
        return s
    }
}

/** One sample at a time. The coloured noises loop a precomputed table; the natural ones are generated live. */
internal class NoiseGenerator(private val kind: NoiseKind, private val rate: Int, private val rng: Random = Random.Default) {
    private val rateD = rate.toDouble()
    private fun white() = rng.nextFloat() * 2f - 1f
    private fun coef(fc: Double) = (1.0 - exp(-2.0 * PI * fc / rateD)).toFloat()
    private fun step(seconds: Double) = 1.0 / (rateD * seconds)

    private val table: FloatArray? =
        if (kind.coloured) Dsp.spectral(1 shl 20, rate, rng, colourSpectrum(kind)) else null
    private var pos = 0

    // Paul Kellet's pink filter, used as the body of natural sounds.
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    private fun pink(w: Float): Float {
        b0 = 0.99886f * b0 + w * 0.0555179f
        b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f
        b5 = -0.7616f * b5 - w * 0.0168980f
        val out = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
        b6 = w * 0.115926f
        return out
    }

    private val voices = Voices(rate, rng)
    private fun brownStep(prev: Float, w: Float) = prev * 0.9995f + w * 0.02f

    // Rain
    private val rainHp = OnePole(coef(300.0)); private val rainLp = OnePole(coef(9000.0))
    private fun rain(dropsPerSec: Double, bedGain: Float, dropGain: Float): Float {
        val p = pink(white())
        val bed = rainLp.process(p - rainHp.process(p))
        if (rng.nextFloat() < dropsPerSec / rateD) {
            val a = rng.nextFloat().pow(3) * dropGain
            voices.spawn(a, 1800.0 + rng.nextDouble().pow(1.5) * 6500.0, 0.002 + rng.nextDouble() * 0.004, a * 0.6f, 0.0005)
        }
        return bed * bedGain + voices.next()
    }

    // Thunder
    private var rumble = 0f; private val rumbleLp = OnePole(coef(140.0)); private val crackHp = OnePole(coef(900.0))
    private var rumbleEnv = 0f; private var crackEnv = 0f; private var ripple = 0.0
    private var untilThunder = (3.0 * rate).toInt()

    // Ocean
    private var u1 = 0.0; private var u2 = 0.45; private var p1 = 10.0; private var p2 = 12.5
    private val oceanLp = OnePole(0.01f); private val foamLp = OnePole(coef(3500.0))
    private fun swell(u: Double) = sin(PI * u.pow(0.65)).let { it * it }

    // Wind
    private val gustLp = OnePole(coef(0.3)); private val gustSd = 0.577f * sqrt(coef(0.3) / 2f)
    private val windA = OnePole(0.1f); private val windB = OnePole(0.02f)
    private var wy1 = 0.0; private var wy2 = 0.0; private var wB0 = 0.0; private var wA1 = 0.0; private var wA2 = 0.0; private var wTick = 0

    // Stream
    private val streamLp = OnePole(coef(2500.0)); private val streamHp = OnePole(coef(350.0)); private val babbleLp = OnePole(coef(0.7))
    private val babbleSd = 0.577f * sqrt(coef(0.7) / 2f)

    // Fire
    private val fireLp = OnePole(coef(450.0)); private var fireBrown = 0f
    private val flickerLp = OnePole(coef(2.0)); private val flickerSd = 0.577f * sqrt(coef(2.0) / 2f)
    private val fireHissLp = OnePole(coef(3500.0))

    // Fan
    private var fanBrown = 0f; private val fanLp = OnePole(coef(1100.0)); private var fanPh = 0.0; private var fanAm = 0.0

    private val pairBuf = FloatArray(2)

    fun next(): Float { nextPair(pairBuf); return pairBuf[0] }

    /**
     * The next left and right samples. A coloured noise reads its table at two points half a loop apart, which gives
     * two unrelated noises with the same spectrum: a wide, steady sound on headphones. Natural sounds are mono.
     */
    fun nextPair(out: FloatArray) {
        val t = table
        if (t != null) {
            out[0] = t[pos]
            out[1] = t[(pos + t.size / 2) and (t.size - 1)]
            pos = (pos + 1) and (t.size - 1)
        } else {
            val v = natural() * gain(kind)
            out[0] = v; out[1] = v
        }
    }

    private fun natural(): Float {
        val w = white()
        return when (kind) {
            NoiseKind.Rain -> rain(110.0, 0.9f, 0.9f)
            NoiseKind.Thunder -> {
                val r = rain(230.0, 1.1f, 0.9f)
                if (--untilThunder <= 0) {
                    untilThunder = ((8.0 + rng.nextDouble() * 18.0) * rate).toInt()
                    crackEnv = 1f; rumbleEnv = 0.6f + rng.nextFloat() * 0.4f; ripple = rng.nextDouble() * 6.28
                }
                crackEnv *= 1f - 1f / (0.07f * rate)
                rumbleEnv *= 1f - 1f / (3.8f * rate)
                ripple += 2 * PI * 2.2 / rateD
                rumble = brownStep(rumble, white())
                val roll = (0.72 + 0.28 * sin(ripple)).toFloat()
                val crack = (w - crackHp.process(w)) * crackEnv
                r + rumbleLp.process(rumble) * 6f * rumbleEnv * roll + crack * 1.4f
            }
            NoiseKind.Ocean -> {
                u1 += step(p1); u2 += step(p2)
                if (u1 >= 1.0) { u1 -= 1.0; p1 = 8.0 + rng.nextDouble() * 5.0 }
                if (u2 >= 1.0) { u2 -= 1.0; p2 = 9.0 + rng.nextDouble() * 6.0 }
                val env = (0.6 * swell(u1) + 0.5 * swell(u2) + 0.1).toFloat()
                oceanLp.set(coef(220.0 + 2600.0 * env.toDouble().pow(1.5)))
                val body = oceanLp.process(pink(w))
                val foam = (w - foamLp.process(w)) * env * env * env
                body * (0.15f + env * 1.9f) + foam * 0.5f
            }
            NoiseKind.Wind -> {
                val gust = (0.45f + gustLp.process(white()) / gustSd * 0.33f).coerceIn(0.06f, 1f)
                val fc = 380.0 + 900.0 * gust
                windA.set(coef(fc)); windB.set(coef(fc / 6.0))
                val air = windA.process(w) - windB.process(w)
                if (wTick++ and 31 == 0) {
                    val f0 = 2 * PI * (520.0 + 420.0 * gust) / rateD
                    val r = 0.9985
                    wA1 = -2 * r * kotlin.math.cos(f0); wA2 = r * r; wB0 = 1 - r
                }
                val y = wB0 * w - wA1 * wy1 - wA2 * wy2
                wy2 = wy1; wy1 = y
                air * gust * 3.2f + y.toFloat() * gust * gust * 1.6f
            }
            NoiseKind.Stream -> {
                val bed = streamLp.process(w) - streamHp.process(w)
                val babble = (0.9f + babbleLp.process(white()) / babbleSd * 0.4f).coerceIn(0.25f, 1.8f)
                if (rng.nextFloat() < 85.0 * babble / rateD) {
                    val f = 600.0 * 6.0.pow(rng.nextDouble())
                    val tau = 0.004 + rng.nextDouble() * 0.01
                    voices.spawn((0.25f + rng.nextFloat() * 0.75f), f, tau, rise = 1.0 + 0.5 + rng.nextDouble() * 0.5, riseSec = 0.018)
                }
                bed * 0.9f + voices.next() * 1.2f
            }
            NoiseKind.Fire -> {
                fireBrown = brownStep(fireBrown, white())
                val flick = (0.6f + flickerLp.process(white()) / flickerSd * 0.3f).coerceIn(0.2f, 1.3f)
                if (rng.nextFloat() < 16.0 * flick / rateD) {
                    val a = rng.nextFloat().pow(2)
                    if (rng.nextFloat() < 0.08f) voices.spawn(0.5f, 900.0 + rng.nextDouble() * 900.0, 0.012, 0.9f, 0.006)
                    else voices.spawn(a * 0.5f, 1800.0 + rng.nextDouble() * 4000.0, 0.0015, a * 0.9f, 0.0008)
                }
                val hiss = w - fireHissLp.process(w)
                fireLp.process(fireBrown * 0.3f) * flick + hiss * 0.025f * flick + voices.next()
            }
            NoiseKind.Fan -> {
                fanBrown = brownStep(fanBrown, w)
                fanPh += 2 * PI * 98.0 / rateD; fanAm += 2 * PI * 31.0 / rateD
                val hum = sin(fanPh) * 0.05 + sin(2 * fanPh) * 0.03 + sin(3 * fanPh) * 0.012
                ((hum + fanBrown * 0.12f + fanLp.process(pink(white())) * 1.1f) * (1.0 + 0.07 * sin(fanAm))).toFloat()
            }
            else -> 0f
        }
    }

    companion object {
        /** Per-sound level trim so every natural sound is about as loud to the ear as the coloured ones. */
        private fun gain(k: NoiseKind) = when (k) {
            NoiseKind.Rain -> 0.49f
            NoiseKind.Thunder -> 0.27f
            NoiseKind.Ocean -> 0.58f
            NoiseKind.Wind -> 0.41f
            NoiseKind.Stream -> 0.195f
            NoiseKind.Fire -> 1.4f
            NoiseKind.Fan -> 1.0f
            else -> 1f
        }

        fun colourSpectrum(kind: NoiseKind): (Double) -> Double = when (kind) {
            NoiseKind.White -> { _ -> 1.0 }
            NoiseKind.Pink -> { f -> 1.0 / sqrt(f) }
            NoiseKind.Brown -> { f -> 1.0 / f }
            NoiseKind.Blue -> { f -> sqrt(f) }
            NoiseKind.Violet -> { f -> f }
            // Inverse of the ear's sensitivity curve (A-weighting), capped at +20 dB so the bass stays bounded.
            NoiseKind.Grey -> { f -> minOf(1.0 / sqrt(Dsp.aPower(f)), 10.0) }
            // The mid-range only: a bump about two octaves wide centred on 500 Hz.
            NoiseKind.Green -> { f -> val o = ln(f / 500.0) / ln(2.0); maxOf(exp(-o * o / (2 * 0.8 * 0.8)), 0.01) }
            else -> { _ -> 1.0 }
        }
    }
}
