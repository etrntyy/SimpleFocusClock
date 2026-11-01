package com.focusclock.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A decoded recording: interleaved 16-bit samples. */
internal class Pcm(val sampleRate: Int, val channels: Int, val data: ShortArray)

/** Real recordings of the natural sounds, bundled as Opus files in assets/sounds. */
internal object Recordings {
    fun assetFor(kind: NoiseKind): String? = when (kind) {
        NoiseKind.Rain -> "sounds/rain.ogg"
        NoiseKind.Thunder -> "sounds/thunder.ogg"
        NoiseKind.Ocean -> "sounds/ocean.ogg"
        NoiseKind.Wind -> "sounds/wind.ogg"
        NoiseKind.Stream -> "sounds/stream.ogg"
        NoiseKind.Fire -> "sounds/fire.ogg"
        NoiseKind.Fan -> "sounds/fan.ogg"
        else -> null
    }

    /** Decodes [path] and joins its end back to its start so it can loop without a seam. */
    fun load(context: Context, path: String): Pcm {
        val pcm = decode(context, path)
        return Pcm(pcm.sampleRate, pcm.channels, seamless(pcm, 3.0))
    }

    private fun decode(context: Context, path: String): Pcm {
        val afd = context.assets.openFd(path)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val format = extractor.getTrackFormat(0)
            extractor.selectTrack(0)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val estimate = if (format.containsKey(MediaFormat.KEY_DURATION))
                (format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 * rate * channels).toInt() + rate * channels else 1 shl 22
            var out = ShortArray(estimate)
            var size = 0
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val n = shorts.remaining()
                        if (size + n > out.size) out = out.copyOf(maxOf(out.size * 2, size + n))
                        shorts.get(out, size, n)
                        size += n
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally {
                codec.stop(); codec.release()
            }
            return Pcm(rate, channels, out.copyOf(size))
        } finally {
            extractor.release()
            afd.close()
        }
    }

    /**
     * Fades the last [seconds] of the recording into its first [seconds] (equal power, so the level stays even)
     * and drops them from the end. The loop then runs from the end straight back to the start with nothing to hear.
     */
    fun seamless(p: Pcm, seconds: Double): ShortArray {
        val ch = p.channels
        val frames = p.data.size / ch
        val x = minOf((seconds * p.sampleRate).toInt(), frames / 4)
        val outFrames = frames - x
        val out = p.data.copyOf(outFrames * ch)
        for (i in 0 until x) {
            val w = i.toDouble() / maxOf(x - 1, 1) * PI / 2
            val gIn = sin(w).toFloat(); val gOut = cos(w).toFloat()
            for (c in 0 until ch) {
                out[i * ch + c] = (p.data[i * ch + c] * gIn + p.data[(outFrames + i) * ch + c] * gOut).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return out
    }
}
