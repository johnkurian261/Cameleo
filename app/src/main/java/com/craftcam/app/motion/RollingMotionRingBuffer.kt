package com.craftcam.app.motion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class EncodedMotionFrame(
    val timestampNs: Long,
    val width: Int,
    val height: Int,
    val jpegBytes: ByteArray
)

data class TemporalFrameParams(
    val frameIndex: Int,
    val totalFrames: Int,
    val timestampNs: Long,
    val presentationTimeNs: Long,
    val crossfadePrevAlpha: Float,
    val grainSeed: Float,
    val exposureFlickerEv: Float,
    val flashBoostEv: Float
) {
    val totalExposureOffsetEv: Float
        get() = exposureFlickerEv + flashBoostEv
}

/**
 * Bounded circular ring buffer of JPEG-compressed motion frames.
 *
 * Strictly stores compact JPEG byte arrays (typically ~40-75 KB per frame)
 * rather than uncompressed Bitmaps, enforcing both maximum frame count and
 * total byte budget so memory remains bounded (~2-5 MB) regardless of session length.
 */
class RollingMotionRingBuffer(
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
    private val maxTotalBytes: Int = DEFAULT_MAX_BYTES,
    private val maxWindowNs: Long = DEFAULT_MAX_WINDOW_NS
) {
    companion object {
        const val DEFAULT_MAX_FRAMES = 45
        const val DEFAULT_MAX_BYTES = 6 * 1024 * 1024 // 6 MB hard cap
        const val DEFAULT_MAX_WINDOW_NS = 2_000_000_000L // 2.0s rolling window
    }

    private val lock = Any()
    private val deque = ArrayDeque<EncodedMotionFrame>(maxFrames + 2)
    private var totalBytes = 0

    val frameCount: Int
        get() = synchronized(lock) { deque.size }

    val retainedBytes: Int
        get() = synchronized(lock) { totalBytes }

    fun push(timestampNs: Long, width: Int, height: Int, jpegBytes: ByteArray) {
        if (jpegBytes.isEmpty() || width <= 0 || height <= 0) return
        val frame = EncodedMotionFrame(timestampNs, width, height, jpegBytes)
        synchronized(lock) {
            deque.addLast(frame)
            totalBytes += jpegBytes.size
            val cutoffNs = timestampNs - maxWindowNs
            while (deque.size > 1 && (
                    deque.size > maxFrames ||
                    totalBytes > maxTotalBytes ||
                    deque.first.timestampNs < cutoffNs
                )
            ) {
                val evicted = deque.removeFirst()
                totalBytes = (totalBytes - evicted.jpegBytes.size).coerceAtLeast(0)
            }
        }
    }

    fun snapshotWindow(
        startTimestampNs: Long,
        endTimestampNs: Long
    ): List<EncodedMotionFrame> = synchronized(lock) {
        deque.filter { it.timestampNs in startTimestampNs..endTimestampNs }
            .sortedBy { it.timestampNs }
    }

    fun snapshotAll(): List<EncodedMotionFrame> = synchronized(lock) {
        deque.sortedBy { it.timestampNs }
    }

    fun clear() {
        synchronized(lock) {
            deque.clear()
            totalBytes = 0
        }
    }
}

/**
 * Bounded circular PCM audio buffer (44.1 kHz, 16-bit mono, up to 3.5 seconds).
 * Only records while Golf Motion Photo and Audio are enabled AND permission is granted.
 */
class RollingAudioRingBuffer(
    private val sampleRateHz: Int = 44_100,
    maxSeconds: Float = 3.5f
) {
    companion object {
        private const val TAG = "RollingAudioRingBuffer"
    }

    private val maxSamples = (sampleRateHz * maxSeconds).roundToInt().coerceAtLeast(sampleRateHz)
    private val samples = ShortArray(maxSamples)
    private val timestampsNs = LongArray(maxSamples)
    private val lock = Any()
    private var writePos = 0
    private var validCount = 0

    @Volatile
    private var running = false
    private var workerThread: Thread? = null

    fun startIfPermitted(context: Context) {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) return

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufBytes * 2, 8192)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "AudioRecord creation skipped", t)
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try { recorder.release() } catch (_: Throwable) {}
            return
        }

        running = true
        val thread = Thread({
            val chunk = ShortArray(1024)
            try {
                recorder.startRecording()
                while (running) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val endNs = android.os.SystemClock.elapsedRealtimeNanos()
                        val nsPerSample = 1_000_000_000.0 / sampleRateHz
                        synchronized(lock) {
                            for (i in 0 until read) {
                                val tNs = endNs - ((read - 1 - i) * nsPerSample).toLong()
                                samples[writePos] = chunk[i]
                                timestampsNs[writePos] = tNs
                                writePos = (writePos + 1) % maxSamples
                                if (validCount < maxSamples) validCount++
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Audio capture loop ended: ${t.message}")
            } finally {
                try { recorder.stop() } catch (_: Throwable) {}
                try { recorder.release() } catch (_: Throwable) {}
            }
        }, "Cameleo-MotionAudio").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
        workerThread = thread
        thread.start()
    }

    fun snapshotPcmWindow(startNs: Long, endNs: Long): ShortArray {
        synchronized(lock) {
            if (validCount == 0 || endNs <= startNs) return ShortArray(0)
            val oldestIdx = if (validCount < maxSamples) 0 else writePos
            val out = ArrayList<Short>(minOf(validCount, ((endNs - startNs) * sampleRateHz / 1_000_000_000L).toInt() + 512))
            for (i in 0 until validCount) {
                val idx = (oldestIdx + i) % maxSamples
                val ts = timestampsNs[idx]
                if (ts in startNs..endNs) {
                    out.add(samples[idx])
                }
            }
            return out.toShortArray()
        }
    }

    fun stopAndClear() {
        running = false
        try {
            workerThread?.join(250L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        workerThread = null
        synchronized(lock) {
            writePos = 0
            validCount = 0
        }
    }
}

/**
 * Deterministic temporal cadence & vintage Golf effect planner.
 * Pure Kotlin math so behavior can be audited and verified offline without a GPU.
 */
object GolfTemporalEffectPlanner {

    data class CadenceSelection<T>(
        val frames: List<T>,
        val params: List<TemporalFrameParams>,
        val shutterOutputIndex: Int,
        val actualDurationMs: Long
    )

    fun <T> planTimeline(
        orderedFrames: List<T>,
        timestampNsOf: (T) -> Long,
        shutterTimestampNs: Long,
        targetDurationMs: Long,
        targetFps: Int,
        flashEffectEnabled: Boolean,
        enableFlicker: Boolean = true
    ): CadenceSelection<T> {
        if (orderedFrames.isEmpty()) {
            return CadenceSelection(emptyList(), emptyList(), 0, 0L)
        }
        if (orderedFrames.size == 1) {
            val f = orderedFrames.first()
            return CadenceSelection(
                frames = listOf(f),
                params = listOf(
                    TemporalFrameParams(
                        frameIndex = 0,
                        totalFrames = 1,
                        timestampNs = timestampNsOf(f),
                        presentationTimeNs = 0L,
                        crossfadePrevAlpha = 0f,
                        grainSeed = 0.13f,
                        exposureFlickerEv = 0f,
                        flashBoostEv = if (flashEffectEnabled) 0.28f else 0f
                    )
                ),
                shutterOutputIndex = 0,
                actualDurationMs = targetDurationMs.coerceAtLeast(1000L)
            )
        }

        val fps = targetFps.coerceIn(12, 30)
        val desiredCount = max(2, ((targetDurationMs / 1000.0) * fps).roundToInt())
        val firstNs = timestampNsOf(orderedFrames.first())
        val lastNs = timestampNsOf(orderedFrames.last())
        val spanNs = (lastNs - firstNs).coerceAtLeast(1L)
        val outFrameDurationNs = 1_000_000_000L / fps

        val selected = ArrayList<T>(desiredCount)
        val params = ArrayList<TemporalFrameParams>(desiredCount)
        var bestShutterIdx = 0
        var bestShutterDelta = Long.MAX_VALUE
        var prevSrcIdx = -1

        for (i in 0 until desiredCount) {
            val u = if (desiredCount > 1) i.toFloat() / (desiredCount - 1) else 0f
            val targetTs = firstNs + (u * spanNs).toLong()
            // Nearest chronological source frame
            var nearestIdx = 0
            var minDist = Long.MAX_VALUE
            for (s in orderedFrames.indices) {
                val dist = abs(timestampNsOf(orderedFrames[s]) - targetTs)
                if (dist < minDist) {
                    minDist = dist
                    nearestIdx = s
                }
            }
            val srcFrame = orderedFrames[nearestIdx]
            val srcTs = timestampNsOf(srcFrame)
            selected.add(srcFrame)

            val shutterDelta = abs(srcTs - shutterTimestampNs)
            if (shutterDelta < bestShutterDelta) {
                bestShutterDelta = shutterDelta
                bestShutterIdx = i
            }

            // Subtle natural crossfade on frame step changes to avoid choppy jumps on stuttered frames
            val crossfade = if (i > 0 && nearestIdx != prevSrcIdx) 0.16f else 0f
            prevSrcIdx = nearestIdx

            // Subtle organic exposure micro-flicker (+/- 0.025 EV max, non-distracting celluloid breathing)
            val flickerEv = if (enableFlicker) {
                (sin(i * 1.91 + 0.4) * 0.014f + sin(i * 4.37) * 0.010f).toFloat().coerceIn(-0.026f, 0.026f)
            } else 0f

            // Shutter-synchronized flash bloom envelope (+0.32 EV at t=0ms -> exponential decay over ~140ms)
            val dtAfterShutterMs = (srcTs - shutterTimestampNs) / 1_000_000.0
            val flashEv = if (flashEffectEnabled && dtAfterShutterMs >= -15.0 && dtAfterShutterMs <= 165.0) {
                val effectiveDt = max(0.0, dtAfterShutterMs)
                (0.32 * exp(-effectiveDt / 55.0)).toFloat().coerceIn(0f, 0.34f)
            } else 0f

            // Distinct deterministic film grain seed per frame (preserves non-repeating organic texture)
            val grainSeed = (((i * 37 + 11) % 97) / 97.0f)

            params.add(
                TemporalFrameParams(
                    frameIndex = i,
                    totalFrames = desiredCount,
                    timestampNs = srcTs,
                    presentationTimeNs = i * outFrameDurationNs,
                    crossfadePrevAlpha = crossfade,
                    grainSeed = grainSeed,
                    exposureFlickerEv = flickerEv,
                    flashBoostEv = flashEv
                )
            )
        }

        // Guarantee controlled brief flash pulse at exact shutter anchor if timestamp sampling skipped dt=0
        if (flashEffectEnabled && params.none { it.flashBoostEv > 0.15f }) {
            params[bestShutterIdx] = params[bestShutterIdx].copy(flashBoostEv = 0.32f)
            if (bestShutterIdx + 1 < params.size) {
                params[bestShutterIdx + 1] = params[bestShutterIdx + 1].copy(flashBoostEv = 0.14f)
            }
        }

        val actualMs = ((desiredCount.toLong() * 1000L) / fps).coerceAtLeast(targetDurationMs)
        return CadenceSelection(
            frames = selected,
            params = params,
            shutterOutputIndex = bestShutterIdx,
            actualDurationMs = actualMs
        )
    }
}
