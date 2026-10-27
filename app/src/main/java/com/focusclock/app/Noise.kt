package com.focusclock.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The coloured noises on offer, each described by how its energy is spread across frequencies. */
enum class NoiseKind(val label: String, val blurb: String) {
    White("White noise", "Equal energy at every frequency. A bright, even hiss."),
    Pink("Pink noise", "Softer highs, like steady rain or wind."),
    Brown("Brown noise", "Deep and rumbling, like a waterfall or distant thunder. Also called red."),
    Blue("Blue noise", "Boosted highs, a thin sharp hiss."),
    Violet("Violet noise", "Even more treble, very bright. Also called purple."),
    Grey("Grey noise", "Balanced to sound equally loud at all pitches."),
    Green("Green noise", "Centred on the mid-range, like the sound of a forest."),
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
            NoiseKind.White -> w
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
            NoiseKind.Green -> {
                val y = bpB0 * w - bpB0 * x2 - bpA1 * y1 - bpA2 * y2
                x2 = x1; x1 = w; y2 = y1; y1 = y
                y * 3f
            }
        }
    }
}
