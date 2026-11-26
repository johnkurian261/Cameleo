package com.craftcam.app.camera.focus

import android.content.Context
import android.graphics.PointF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.model.CameraHardwareCapabilities
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Standard Android Camera2 Autofocus States.
 */
enum class AutofocusStatus(val rawValue: Int, val displayName: String) {
    INACTIVE(CaptureResult.CONTROL_AF_STATE_INACTIVE, "Inactive"),
    PASSIVE_SCAN(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN, "Scanning (Continuous)"),
    PASSIVE_FOCUSED(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED, "Focused (Continuous)"),
    ACTIVE_SCAN(CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN, "Scanning (Tap)"),
    FOCUSED_LOCKED(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED, "Focused (Locked)"),
    NOT_FOCUSED_LOCKED(CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED, "Unfocused (Locked)"),
    PASSIVE_UNFOCUSED(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED, "Unfocused (Continuous)"),
    FIXED_FOCUS(-1, "Fixed Focus");

    companion object {
        fun fromCamera2(value: Int?, isFixedFocus: Boolean): AutofocusStatus {
            if (isFixedFocus) return FIXED_FOCUS
            return when (value) {
                CaptureResult.CONTROL_AF_STATE_INACTIVE -> INACTIVE
                CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> PASSIVE_SCAN
                CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> PASSIVE_FOCUSED
                CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> ACTIVE_SCAN
                CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> FOCUSED_LOCKED
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> NOT_FOCUSED_LOCKED
                CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> PASSIVE_UNFOCUSED
                else -> INACTIVE
            }
        }
    }
}

/**
 * Viewfinder / sensor normalized coordinate point [0f, 1f].
 */
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * Comprehensive real-time autofocus telemetry.
 */
data class FocusTelemetry(
    val status: AutofocusStatus = AutofocusStatus.INACTIVE,
    val rawAfState: Int = CaptureResult.CONTROL_AF_STATE_INACTIVE,
    val afMode: Int = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    val lensDistanceDiopters: Float = 0.0f,
    val approxDistanceMeters: Float = Float.POSITIVE_INFINITY,
    val isLensMoving: Boolean = false,
    val isFocused: Boolean = false,
    val isLocked: Boolean = false,
    val tapPoint: NormalizedPoint? = null,
    val minFocusDistanceDiopters: Float = 10.0f,
    val hyperfocalDistanceDiopters: Float = 0.28f,
    val isSubjectTooClose: Boolean = false,
    val lastConvergenceDurationMs: Long = 0L,
    val scanDurationMs: Long = 0L,
    val cameraId: String = "0",
    val isFixedFocus: Boolean = false,
    val lensFacing: Int = 1, // 1 = BACK, 0 = FRONT
    val lastEventTimestamp: Long = 0L
)

/**
 * Historical diagnostic log entry for Developer Focus Inspector.
 */
data class FocusLogEntry(
    val timestampMs: Long,
    val formattedTime: String,
    val fromState: AutofocusStatus,
    val toState: AutofocusStatus,
    val diopters: Float,
    val isLensMoving: Boolean,
    val message: String
)

/**
 * Hardware-Aware Central Autofocus Controller.
 *
 * Enforces:
 * 1. Continuous Picture Autofocus (CONTROL_AF_MODE_CONTINUOUS_PICTURE) as standard baseline.
 * 2. Accurate tap-to-focus with aspect-ratio-corrected sensor coordinate projection.
 * 3. Graceful fixed-focus fallback on ultra-wide lenses (e.g. S21 13mm lens).
 * 4. Pre-capture settling coordination to eliminate out-of-focus shutter snaps.
 * 5. AE/AF Lock support via long-press gestures.
 * 6. Minimum focus distance violation warnings for macro scenes.
 * 7. Real-time telemetry and developer diagnostic inspector events.
 */
class AutofocusController(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AutofocusController"
        private const val MAX_LOG_ENTRIES = 40
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private val _telemetry = MutableStateFlow(FocusTelemetry())
    val telemetry: StateFlow<FocusTelemetry> = _telemetry.asStateFlow()

    private val logEntries = ConcurrentLinkedDeque<FocusLogEntry>()

    private var scanStartTimeMs: Long = 0L
    private var isScanningActive: Boolean = false
    private var currentCapabilities: CameraHardwareCapabilities? = null

    // Track active tap-to-focus metering future
    private var activeMeteringFuture: ListenableFuture<FocusMeteringResult>? = null

    /**
     * Updates hardware capabilities when switching cameras or binding new use-cases.
     */
    fun onCameraCapabilitiesChanged(caps: CameraHardwareCapabilities, lensFacing: Int) {
        currentCapabilities = caps
        val isFixed = caps.isFixedFocus || caps.minFocusDistance <= 0.0f ||
                (caps.availableAfModes.size == 1 && caps.availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF))

        _telemetry.update { current ->
            current.copy(
                cameraId = caps.cameraId,
                isFixedFocus = isFixed,
                minFocusDistanceDiopters = caps.minFocusDistance,
                hyperfocalDistanceDiopters = caps.hyperfocalDistance,
                lensFacing = lensFacing,
                status = if (isFixed) AutofocusStatus.FIXED_FOCUS else AutofocusStatus.INACTIVE
            )
        }
        addLogEntry("Camera caps changed: ID=${caps.cameraId}, FixedFocus=$isFixed, MinDist=${caps.minFocusDistance}D")
    }

    /**
     * Ingests real-time CaptureResult metadata from CameraCaptureSession.
     */
    fun onCaptureResult(result: CaptureResult) {
        val now = SystemClock.elapsedRealtime()
        val caps = currentCapabilities
        val isFixed = _telemetry.value.isFixedFocus

        val rawAfState = result.get(CaptureResult.CONTROL_AF_STATE) ?: CaptureResult.CONTROL_AF_STATE_INACTIVE
        val afMode = result.get(CaptureResult.CONTROL_AF_MODE) ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        val lensDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: _telemetry.value.lensDistanceDiopters
        val lensState = result.get(CaptureResult.LENS_STATE) ?: CaptureResult.LENS_STATE_STATIONARY

        val isLensMoving = lensState == CaptureResult.LENS_STATE_MOVING
        val status = AutofocusStatus.fromCamera2(rawAfState, isFixed)

        // Calculate physical distance in meters (approx 1 / diopters)
        val approxMeters = if (lensDist > 0.05f) 1.0f / lensDist else Float.POSITIVE_INFINITY

        // Check subject too close (minimum focus distance violation)
        val minDist = caps?.minFocusDistance ?: 10.0f
        val isSubjectTooClose = !isFixed && minDist > 0.0f && lensDist >= (minDist * 0.94f) &&
                (status == AutofocusStatus.PASSIVE_UNFOCUSED || status == AutofocusStatus.NOT_FOCUSED_LOCKED)

        val previousStatus = _telemetry.value.status
        val isFocused = when (status) {
            AutofocusStatus.PASSIVE_FOCUSED,
            AutofocusStatus.FOCUSED_LOCKED,
            AutofocusStatus.FIXED_FOCUS -> true
            else -> false
        }

        // Track scan duration & convergence latency
        var convergenceDuration = _telemetry.value.lastConvergenceDurationMs
        var scanDuration = _telemetry.value.scanDurationMs

        if (status == AutofocusStatus.PASSIVE_SCAN || status == AutofocusStatus.ACTIVE_SCAN) {
            if (!isScanningActive) {
                isScanningActive = true
                scanStartTimeMs = now
            }
            scanDuration = now - scanStartTimeMs
        } else if (isScanningActive && (status == AutofocusStatus.PASSIVE_FOCUSED || status == AutofocusStatus.FOCUSED_LOCKED)) {
            isScanningActive = false
            convergenceDuration = now - scanStartTimeMs
            scanDuration = 0L
        } else if (isScanningActive && (status == AutofocusStatus.PASSIVE_UNFOCUSED || status == AutofocusStatus.NOT_FOCUSED_LOCKED)) {
            isScanningActive = false
            scanDuration = 0L
        }

        if (previousStatus != status) {
            val msg = "State: ${previousStatus.displayName} -> ${status.displayName} (d=${"%.2f".format(lensDist)}D, ${"%.2f".format(approxMeters)}m)"
            addLogEntry(msg, previousStatus, status, lensDist, isLensMoving)
        }

        _telemetry.update { current ->
            current.copy(
                status = status,
                rawAfState = rawAfState,
                afMode = afMode,
                lensDistanceDiopters = lensDist,
                approxDistanceMeters = approxMeters,
                isLensMoving = isLensMoving,
                isFocused = isFocused,
                isSubjectTooClose = isSubjectTooClose,
                lastConvergenceDurationMs = convergenceDuration,
                scanDurationMs = scanDuration,
                lastEventTimestamp = now
            )
        }
    }

    /**
     * Calculates the normalized touch point in sensor coordinates [0f, 1f]
     * accounting for viewfinder aspect ratio letterboxing and viewport padding.
     */
    fun calculateNormalizedPoint(
        tapX: Float,
        tapY: Float,
        viewWidth: Int,
        viewHeight: Int,
        aspectRatio: AspectRatioMode
    ): NormalizedPoint {
        if (viewWidth <= 0 || viewHeight <= 0) return NormalizedPoint(0.5f, 0.5f)

        val targetRatio = 1f / aspectRatio.ratio
        val frameW: Float
        val frameH: Float

        val containerRatio = viewHeight.toFloat() / viewWidth.toFloat()
        if (containerRatio > targetRatio) {
            // Viewport is taller than target aspect ratio (letterboxed vertically)
            frameW = viewWidth.toFloat()
            frameH = frameW * targetRatio
        } else {
            // Viewport is wider than target aspect ratio (letterboxed horizontally)
            frameH = viewHeight.toFloat()
            frameW = frameH / targetRatio
        }

        val frameLeft = (viewWidth - frameW) / 2f
        val frameTop = (viewHeight - frameH) / 2f

        val normX = ((tapX - frameLeft) / frameW).coerceIn(0.0f, 1.0f)
        val normY = ((tapY - frameTop) / frameH).coerceIn(0.0f, 1.0f)

        return NormalizedPoint(normX, normY)
    }

    /**
     * Executes reliable tap-to-focus metering on supported cameras.
     */
    fun triggerTapToFocus(
        cameraControl: CameraControl?,
        tapX: Float,
        tapY: Float,
        viewWidth: Int,
        viewHeight: Int,
        aspectRatio: AspectRatioMode,
        autoCancelSeconds: Long = 3L
    ) {
        if (cameraControl == null) return

        if (_telemetry.value.isFixedFocus) {
            addLogEntry("Tap-to-focus ignored: Lens is fixed-focus")
            return
        }

        val normPoint = calculateNormalizedPoint(tapX, tapY, viewWidth, viewHeight, aspectRatio)
        val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val meteringPoint = factory.createPoint(normPoint.x, normPoint.y)

        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(autoCancelSeconds, TimeUnit.SECONDS)
            .build()

        activeMeteringFuture?.cancel(true)

        _telemetry.update { it.copy(tapPoint = NormalizedPoint(tapX, tapY), isLocked = false) }
        addLogEntry("Tap-to-focus triggered at norm=(${normPoint.x.format(2)}, ${normPoint.y.format(2)})")

        val future = cameraControl.startFocusAndMetering(action)
        activeMeteringFuture = future

        future.addListener({
            try {
                val result = future.get()
                val isSuccess = result.isFocusSuccessful
                addLogEntry("Tap-to-focus result: success=$isSuccess")
            } catch (e: Exception) {
                // Cancelled or superseded
            }
        }, context?.mainExecutor ?: java.util.concurrent.Executor { it.run() })
    }

    /**
     * Locks autofocus and auto-exposure at the selected point (AE/AF Lock).
     */
    fun lockFocusAndExposure(
        cameraControl: CameraControl?,
        tapX: Float,
        tapY: Float,
        viewWidth: Int,
        viewHeight: Int,
        aspectRatio: AspectRatioMode
    ) {
        if (cameraControl == null) return
        if (_telemetry.value.isFixedFocus) return

        val normPoint = calculateNormalizedPoint(tapX, tapY, viewWidth, viewHeight, aspectRatio)
        val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val meteringPoint = factory.createPoint(normPoint.x, normPoint.y)

        // Disable auto-cancel for locked focus
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()

        activeMeteringFuture?.cancel(true)
        _telemetry.update { it.copy(tapPoint = NormalizedPoint(tapX, tapY), isLocked = true) }
        addLogEntry("AE/AF Locked at (${normPoint.x.format(2)}, ${normPoint.y.format(2)})")

        activeMeteringFuture = cameraControl.startFocusAndMetering(action)
    }

    /**
     * Unlocks AE/AF and restores continuous picture autofocus.
     */
    fun unlockFocusAndExposure(cameraControl: CameraControl?) {
        if (cameraControl == null) return
        activeMeteringFuture?.cancel(true)
        cameraControl.cancelFocusAndMetering()
        _telemetry.update { it.copy(isLocked = false, tapPoint = null) }
        addLogEntry("AE/AF Unlocked: Restored Continuous Picture AF")
    }

    /**
     * Coordinates shutter release with autofocus settling.
     *
     * 1. If camera is fixed focus -> Ready immediately.
     * 2. If camera is already focused and lens stationary -> Ready immediately.
     * 3. If camera is scanning -> Awaits focus convergence with bounded timeout (max 700ms).
     * 4. If timeout expires -> Proceeds with capture (best-effort, zero lockup).
     */
    suspend fun coordinateShutterWithFocus(
        timeoutMs: Long = 700L
    ): Boolean = withContext(Dispatchers.Default) {
        val current = _telemetry.value
        if (current.isFixedFocus) {
            addLogEntry("Shutter ready immediately (Fixed focus)")
            return@withContext true
        }

        if (current.isFocused && !current.isLensMoving) {
            addLogEntry("Shutter ready immediately (Already focused)")
            return@withContext true
        }

        addLogEntry("Shutter awaiting focus convergence (timeout=${timeoutMs}ms)...")
        val startTime = SystemClock.elapsedRealtime()

        try {
            withTimeout(timeoutMs) {
                while (isActive) {
                    val tel = _telemetry.value
                    if (tel.isFocused && !tel.isLensMoving) {
                        val elapsed = SystemClock.elapsedRealtime() - startTime
                        addLogEntry("Focus converged for capture in ${elapsed}ms")
                        return@withTimeout true
                    }
                    delay(25)
                }
                true
            }
        } catch (e: TimeoutCancellationException) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            addLogEntry("Focus convergence timeout after ${elapsed}ms: capturing with best-effort")
            false
        }
    }

    /**
     * Returns the formatted developer focus diagnostic report.
     */
    fun getDiagnosticReport(): String {
        val t = _telemetry.value
        return buildString {
            appendLine("=== CAMELEO AUTOFOCUS DIAGNOSTIC INSPECTOR ===")
            appendLine("Camera ID: ${t.cameraId} (${if (t.lensFacing == 0) "Front" else "Rear"})")
            appendLine("Fixed Focus: ${t.isFixedFocus}")
            appendLine("AF Status: ${t.status.displayName} (raw=${t.rawAfState})")
            appendLine("AF Mode: ${t.afMode}")
            appendLine("Lens Distance: ${"%.2f".format(t.lensDistanceDiopters)} D (~${"%.2f".format(t.approxDistanceMeters)} m)")
            appendLine("Lens Moving: ${t.isLensMoving}")
            appendLine("Is Focused: ${t.isFocused} | Locked: ${t.isLocked}")
            appendLine("Min Focus Dist: ${t.minFocusDistanceDiopters} D | Hyperfocal: ${t.hyperfocalDistanceDiopters} D")
            appendLine("Subject Too Close Warning: ${t.isSubjectTooClose}")
            appendLine("Last Convergence Time: ${t.lastConvergenceDurationMs} ms")
            appendLine("Active Scan Duration: ${t.scanDurationMs} ms")
            appendLine("Tap Coordinate: ${t.tapPoint?.let { "(${it.x.toInt()}, ${it.y.toInt()})" } ?: "None"}")
            appendLine("--- Event Timeline (Recent ${logEntries.size}) ---")
            for (entry in logEntries) {
                appendLine("[${entry.formattedTime}] ${entry.message}")
            }
        }
    }

    fun getRecentLogEntries(): List<FocusLogEntry> = logEntries.toList()

    private fun addLogEntry(
        message: String,
        fromState: AutofocusStatus = _telemetry.value.status,
        toState: AutofocusStatus = _telemetry.value.status,
        diopters: Float = _telemetry.value.lensDistanceDiopters,
        lensMoving: Boolean = _telemetry.value.isLensMoving
    ) {
        val now = System.currentTimeMillis()
        val entry = FocusLogEntry(
            timestampMs = now,
            formattedTime = TIME_FORMAT.format(Date(now)),
            fromState = fromState,
            toState = toState,
            diopters = diopters,
            isLensMoving = lensMoving,
            message = message
        )
        logEntries.addFirst(entry)
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.removeLast()
        }
        Log.d(TAG, message)
    }

    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
}
