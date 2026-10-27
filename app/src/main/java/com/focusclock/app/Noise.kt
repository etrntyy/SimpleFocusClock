package com.focusclock.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** The noises on offer: the coloured ones are shaped by frequency, the rest imitate natural sounds. */
enum class NoiseKind(val label: String) {
    White("White noise"),
    Pink("Pink noise"),
    Brown("Brown noise"),
    Blue("Blue noise"),
    Violet("Violet noise"),
    Grey("Grey noise"),
    Green("Green noise"),
    Rain("Rain"),
    Thunder("Thunderstorm"),
    Ocean("Ocean waves"),
    Wind("Wind"),
    Stream("Stream"),
    Fire("Campfire"),
    Fan("Fan"),
}

/** Streams generated noise through an [AudioTrack]. Alarm usage keeps it audible under alarms-only Do Not Disturb. */
class NoisePlayer {
    private var thread: Thread? = null
    @Volatile private var running = false

    fun play(kind: NoiseKind) {
        stop()
        running = true
        thread = Thread({ run(kind) }, "noise").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun run(kind: NoiseKind) {
        val rate = 44_100
        val buf = ShortArray(2048)
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, buf.size * 4))
            .build()
        val gen = NoiseGenerator(kind, rate)
        track.play()
        try {
            while (running) {
                for (i in buf.indices) buf[i] = (gen.next().coerceIn(-1f, 1f) * 32767f * 0.6f).toInt().toShort()
                track.write(buf, 0, buf.size)
            }
        } finally {
            track.stop()
            track.release()
        }
    }
}

/** One sample at a time, shaped from white noise by simple filters. */
internal class NoiseGenerator(private val kind: NoiseKind, rate: Int, private val rng: Random = Random.Default) {
    // Pink: Paul Kellet's filter.
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    private var brown = 0f
    private var prevPink = 0f
    private var prevWhite = 0f
    private var low = 0f
    // Green: band-pass around 500 Hz.
    private val bpB0: Float
    private val bpA1: Float
    private val bpA2: Float
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

    init {
        val w0 = 2.0 * PI * 500.0 / rate
        val alpha = sin(w0) / (2.0 * 0.8)
        val a0 = 1.0 + alpha
        bpB0 = (alpha / a0).toFloat()
        bpA1 = (-2.0 * cos(w0) / a0).toFloat()
        bpA2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun white() = rng.nextFloat() * 2f - 1f

    private val rateD = rate.toDouble()
    private fun coef(fc: Double) = (1.0 - exp(-2.0 * PI * fc / rateD)).toFloat()
    private fun phaseStep(seconds: Double) = 2.0 * PI / (rateD * seconds)

    // Natural sounds.
    private var hissLp = 0f
    private var dropEnv = 0f
    private var thunderEnv = 0f
    private var thunderLp = 0f
    private var oceanLp = 0f
    private var oceanHiss = 0f
    private var phA = 0.0
    private var phB = 0.0
    private var windA = 0f
    private var windB = 0f
    private var streamHi = 0f
    private var streamLo = 0f
    private var streamSlow = 0f
    private var fireBrown = 0f
    private var fireLp = 0f
    private var crackEnv = 0f
    private var fanBrown = 0f
    private var fanLp = 0f
    private val aHiss = coef(1500.0)
    private val aThunder = coef(80.0)
    private val aStreamHi = coef(4000.0)
    private val aStreamLo = coef(700.0)
    private val aStreamSlow = coef(14.0)
    private val aFire = coef(2000.0)
    private val aFan = coef(1200.0)
    private val aOceanHiss = coef(1500.0)

    private fun rain(w: Float): Float {
        hissLp += aHiss * (w - hissLp)
        if (rng.nextFloat() < 0.0007f) dropEnv = 0.5f + rng.nextFloat() * 0.5f
        dropEnv *= 0.994f
        return (w - hissLp) * 0.35f + pink(w) * 0.9f + white() * dropEnv * 0.5f
    }

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

    fun next(): Float {
        val w = white()
        return when (kind) {
            NoiseKind.White -> w * 0.6f
            NoiseKind.Pink -> pink(w) * 1.6f
            NoiseKind.Brown -> {
                brown = (brown + 0.02f * w) / 1.02f
                brown * 5f
            }
            NoiseKind.Blue -> {
                val p = pink(w)
                val out = (p - prevPink) * 2.5f
                prevPink = p
                out
            }
            NoiseKind.Violet -> {
                val out = (w - prevWhite) * 0.4f
                prevWhite = w
                out
            }
            NoiseKind.Grey -> {
                // White with the lows and the highs lifted, roughly an inverse equal-loudness curve.
                low += 0.03f * (w - low)
                val high = w - prevWhite
                prevWhite = w
                0.35f * w + 2f * low + 0.25f * high
            }
            NoiseKind.Rain -> rain(w)
            NoiseKind.Thunder -> {
                if (rng.nextFloat() < 1f / (rateD * 14.0).toFloat()) thunderEnv = 1f
                thunderEnv *= 1f - 1f / (rateD * 3.5).toFloat()
                thunderLp += aThunder * (white() - thunderLp)
                rain(w) * 0.7f + thunderLp * (0.06f + thunderEnv) * 7f
            }
            NoiseKind.Ocean -> {
                phA += phaseStep(9.0); phB += phaseStep(13.7)
                val env = (0.5 + 0.5 * sin(phA)) * (0.7 + 0.3 * sin(phB))
                val e = 0.25f + 0.75f * env.toFloat()
                oceanLp += (0.015f + 0.25f * e * e) * (pink(w) - oceanLp)
                oceanHiss += aOceanHiss * (w - oceanHiss)
                oceanLp * e * 2.4f + (w - oceanHiss) * e * e * e * 0.18f
            }
            NoiseKind.Wind -> {
                phA += phaseStep(7.3); phB += phaseStep(3.1)
                val gust = (0.25 + 0.75 * (0.5 + 0.5 * sin(phA)) * (0.65 + 0.35 * sin(phB))).toFloat()
                val fc = 250.0 + 650.0 * gust
                windA += coef(fc) * (w - windA)
                windB += coef(fc * 0.25) * (w - windB)
                (windA - windB) * gust * 5f
            }
            NoiseKind.Stream -> {
                streamHi += aStreamHi * (w - streamHi)
                streamLo += aStreamLo * (w - streamLo)
                streamSlow += aStreamSlow * (white() - streamSlow)
                val burble = (0.7f + streamSlow * 18f).coerceIn(0.2f, 1.6f)
                (streamHi - streamLo) * burble * 1.6f
            }
            NoiseKind.Fire -> {
                fireBrown = (fireBrown + 0.02f * w) / 1.02f
                fireLp += aFire * (white() - fireLp)
                if (rng.nextFloat() < 0.0012f) crackEnv = rng.nextFloat() * 0.7f
                if (rng.nextFloat() < 0.00004f) crackEnv = 1f
                crackEnv *= 0.99f
                fireBrown * 2f + fireLp * 0.5f + white() * crackEnv * 0.8f
            }
            NoiseKind.Fan -> {
                phA += 2.0 * PI * 95.0 / rateD
                phB += phaseStep(5.0)
                fanBrown = (fanBrown + 0.02f * w) / 1.02f
                fanLp += aFan * (pink(w) - fanLp)
                val hum = sin(phA) * 0.06 + sin(2 * phA) * 0.035
                ((hum + fanBrown * 1.5f + fanLp * 0.5f) * (1.0 + 0.06 * sin(phB))).toFloat()
            }
            NoiseKind.Green -> {
                val y = bpB0 * w - bpB0 * x2 - bpA1 * y1 - bpA2 * y2
                x2 = x1; x1 = w; y2 = y1; y1 = y
                y * 3f
            }
        }
    }
}
