package com.focusclock.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.content.res.AssetFileDescriptor
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

/**
 * Turns a recording into an endless, seamless loop as it is being decoded, so a sound starts at once instead of waiting
 * for the whole file to be decoded first. Each pass plays the recording straight through until [crossfadeFrames] before its
 * end, keeps that last stretch, and fades it into the first stretch (saved on the first pass), so the loop has no seam.
 * Every later pass skips what the fade already played. [estimatedFrames] only has to be roughly right.
 */
internal class LoopSplicer(private val channels: Int, private val crossfadeFrames: Int, estimatedFrames: Long) {
    private val head = ShortArray(crossfadeFrames * channels)
    private var tail = ShortArray(crossfadeFrames * channels)
    private var tailShorts = 0
    private var skip = 0L
    private var stopAt = maxOf(estimatedFrames - crossfadeFrames, crossfadeFrames.toLong())
    private var seen = 0L
    private var firstPass = true

    fun beginPass() {
        seen = 0
        tailShorts = 0
    }

    /** Takes the next decoded [chunk] (interleaved) and passes on whatever is to be played. */
    fun feed(chunk: ShortArray, emit: (ShortArray, Int, Int) -> Unit) {
        val frames = chunk.size / channels
        val from = seen
        val to = seen + frames
        if (firstPass && from < crossfadeFrames) {
            val n = (minOf(to, crossfadeFrames.toLong()) - from).toInt()
            chunk.copyInto(head, (from * channels).toInt(), 0, n * channels)
        }
        val playFrom = maxOf(from, skip)
        val playTo = minOf(to, stopAt)
        if (playTo > playFrom) emit(chunk, ((playFrom - from) * channels).toInt(), ((playTo - playFrom) * channels).toInt())
        val tailFrom = maxOf(from, stopAt)
        if (to > tailFrom && tailShorts < tail.size) {
            val offset = ((tailFrom - from) * channels).toInt()
            val n = minOf(((to - tailFrom) * channels).toInt(), tail.size - tailShorts)
            chunk.copyInto(tail, tailShorts, offset, offset + n)
            tailShorts += n
        }
        seen = to
    }

    /** The recording has ended: fade its tail into the head, and get ready for the next pass. */
    fun endPass(emit: (ShortArray, Int, Int) -> Unit) {
        val frames = tailShorts / channels
        if (frames > 0) {
            val out = ShortArray(frames * channels)
            for (i in 0 until frames) {
                val w = i.toDouble() / maxOf(frames - 1, 1) * PI / 2
                val gIn = sin(w).toFloat()
                val gOut = cos(w).toFloat()
                for (c in 0 until channels) {
                    out[i * channels + c] = (head[i * channels + c] * gIn + tail[i * channels + c] * gOut).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            emit(out, 0, out.size)
        }
        skip = frames.toLong()
        stopAt = maxOf(seen - crossfadeFrames, skip + 1)
        firstPass = false
    }
}

/** Reads a bundled recording from the start, a chunk at a time, as the decoder produces it. */
internal class RecordingSource(context: Context, private val path: String) {
    private val afd: AssetFileDescriptor = context.assets.openFd(path)
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false
    var sampleRate = 48_000
        private set
    var channels = 2
        private set
    var estimatedFrames = 0L
        private set

    init {
        try {
            open()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun open() {
        val ex = MediaExtractor()
        extractor = ex
        ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        val format = ex.getTrackFormat(0)
        ex.selectTrack(0)
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        estimatedFrames = if (format.containsKey(MediaFormat.KEY_DURATION)) (format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 * sampleRate).toLong() else sampleRate * 60L
        val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        c.configure(format, null, null, 0)
        c.start()
        codec = c
        inputDone = false
        outputDone = false
    }

    /** Goes back to the start of the recording. */
    fun restart() {
        release()
        open()
    }

    /** The next stretch of decoded samples, or null once the recording has ended. */
    fun next(): ShortArray? {
        val c = codec ?: return null
        val ex = extractor ?: return null
        while (!outputDone) {
            if (!inputDone) {
                val i = c.dequeueInputBuffer(0)
                if (i >= 0) {
                    val buf = c.getInputBuffer(i)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) {
                        c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = c.dequeueOutputBuffer(info, 5_000)
            if (o >= 0) {
                val buf = c.getOutputBuffer(o)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                val out = ShortArray(shorts.remaining())
                shorts.get(out)
                c.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                if (out.isNotEmpty()) return out
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                sampleRate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        return null
    }

    private fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
    }

    fun close() {
        release()
        runCatching { afd.close() }
    }
}
