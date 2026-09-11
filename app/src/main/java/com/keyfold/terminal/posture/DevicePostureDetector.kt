package com.keyfold.terminal.posture

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.keyfold.terminal.model.HeightConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DevicePostureDetector(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    companion object {
        private const val TAG = "KeyFoldPosture"
        private const val FOLD_SCREEN_MIN_SW_DP = 600
        private const val SENSOR_TYPE_HINGE_ANGLE = 36 // Sensor.TYPE_HINGE_ANGLE
    }

    private val windowInfoTracker = WindowInfoTracker.getOrCreate(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hingeSensor = sensorManager?.getDefaultSensor(SENSOR_TYPE_HINGE_ANGLE)

    private val _posture = MutableStateFlow(computeInitialPosture())
    val posture: StateFlow<DevicePosture> = _posture.asStateFlow()

    var currentHingeBounds: Rect? = null
        private set

    private var latestHingeAngle: Float? = null
    private var windowInfoJob: Job? = null
    private var onPostureChanged: ((DevicePosture, Rect?) -> Unit)? = null

    fun setOnPostureChangedListener(listener: (DevicePosture, Rect?) -> Unit) {
        this.onPostureChanged = listener
    }

    fun start() {
        // 1. Listen for Hinge Angle Sensor if available
        hingeSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // 2. Collect WindowLayoutInfo from Jetpack WindowManager
        windowInfoJob?.cancel()
        windowInfoJob = scope.launch(Dispatchers.Main) {
            try {
                windowInfoTracker.windowLayoutInfo(context).collect { info ->
                    processWindowLayoutInfo(info)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "WindowInfoTracker error, falling back to sensor/configuration: ${e.message}")
            }
        }

        // Evaluate immediately
        updatePosture()
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        windowInfoJob?.cancel()
        windowInfoJob = null
    }

    fun onConfigurationChanged() {
        updatePosture()
    }

    private fun processWindowLayoutInfo(info: WindowLayoutInfo) {
        val foldingFeature = info.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        currentHingeBounds = foldingFeature?.bounds
        updatePosture(foldingFeature)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == SENSOR_TYPE_HINGE_ANGLE && event.values.isNotEmpty()) {
            latestHingeAngle = event.values[0]
            updatePosture()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun computeInitialPosture(): DevicePosture {
        val config = context.resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isUnfolded = config.smallestScreenWidthDp >= FOLD_SCREEN_MIN_SW_DP

        return when {
            isUnfolded && isLandscape -> DevicePosture.UNFOLDED_LANDSCAPE_HALF
            isUnfolded && !isLandscape -> DevicePosture.UNFOLDED_PORTRAIT_FLAT
            !isUnfolded && isLandscape -> DevicePosture.FOLDED_LANDSCAPE
            else -> DevicePosture.FOLDED_PORTRAIT
        }
    }

    private fun updatePosture(foldingFeature: FoldingFeature? = null) {
        val config = context.resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isUnfolded = config.smallestScreenWidthDp >= FOLD_SCREEN_MIN_SW_DP

        val isHalfOpened = when {
            foldingFeature != null -> foldingFeature.state == FoldingFeature.State.HALF_OPENED
            latestHingeAngle != null -> {
                val angle = latestHingeAngle ?: 180f
                angle in 65f..125f
            }
            else -> false
        }

        val newPosture = when {
            isUnfolded -> {
                if (isLandscape) {
                    if (isHalfOpened) DevicePosture.UNFOLDED_LANDSCAPE_HALF
                    else DevicePosture.UNFOLDED_LANDSCAPE_FLAT
                } else {
                    if (isHalfOpened) DevicePosture.UNFOLDED_PORTRAIT_HALF
                    else DevicePosture.UNFOLDED_PORTRAIT_FLAT
                }
            }
            else -> {
                if (isLandscape) DevicePosture.FOLDED_LANDSCAPE
                else DevicePosture.FOLDED_PORTRAIT
            }
        }

        if (_posture.value != newPosture) {
            _posture.value = newPosture
            onPostureChanged?.invoke(newPosture, currentHingeBounds)
        }
    }

    /**
     * Calculates the ideal keyboard height in pixels based on the posture and layout height config.
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateKeyboardHeight(posture: DevicePosture, heightConfig: HeightConfig): Int {
        val displayMetrics = context.resources.displayMetrics
        val totalScreenHeight = displayMetrics.heightPixels
        val offsetPx = (heightConfig.offsetDp * displayMetrics.density).toInt()
        val percent = if (heightConfig.percent > 0) heightConfig.percent else heightConfig.fallbackPercent

        val baseHeight = when (heightConfig.mode) {
            "fold_hinge" -> {
                val bounds = currentHingeBounds
                if (bounds != null && bounds.bottom > 0 && bounds.bottom < totalScreenHeight) {
                    // Lower half below the hinge crease
                    totalScreenHeight - bounds.bottom
                } else {
                    (totalScreenHeight * (percent / 100f)).toInt()
                }
            }
            "dp" -> {
                (heightConfig.fixedDp * displayMetrics.density).toInt()
            }
            "percentage" -> {
                (totalScreenHeight * (percent / 100f)).toInt()
            }
            else -> {
                (totalScreenHeight * (percent / 100f)).toInt()
            }
        }

        return (baseHeight + offsetPx).coerceIn(150, (totalScreenHeight * 0.85f).toInt())
    }
}
