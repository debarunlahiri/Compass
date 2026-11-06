package com.lambrk.compass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow

/** Which hardware sensors this device actually has. */
data class SensorAvailability(
    val magnetometer: Boolean,
    val accelerometer: Boolean,
    val rotationVector: Boolean,
    val gyroscope: Boolean,
    val gravity: Boolean,
    val light: Boolean,
) {
    val canShowHeading: Boolean get() = magnetometer && (rotationVector || accelerometer)
    val canShowLevel: Boolean get() = gravity || accelerometer
}

enum class SensorAccuracy {
    UNRELIABLE, LOW, MEDIUM, HIGH, UNKNOWN;

    val needsCalibration: Boolean get() = this == UNRELIABLE || this == LOW

    companion object {
        fun from(status: Int): SensorAccuracy = when (status) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> LOW
            SensorManager.SENSOR_STATUS_UNRELIABLE -> UNRELIABLE
            else -> UNKNOWN
        }
    }
}

/** Raw magnetic heading in degrees [0, 360), relative to the top of the screen. */
data class HeadingSample(val azimuth: Float, val accuracy: SensorAccuracy)

/** Gravity in screen coordinates: +x = right edge up, +y = top edge up, +z = face up. */
data class GravitySample(val x: Float, val y: Float, val z: Float)

class SensorRepository(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val availability = SensorAvailability(
        magnetometer = magnetometer != null,
        accelerometer = accelerometer != null,
        rotationVector = rotationVector != null,
        gyroscope = gyroscope != null,
        gravity = gravitySensor != null,
        light = lightSensor != null,
    )

    private fun displayRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    /**
     * Compass heading. Prefers the fused rotation-vector sensor (gyro-stabilised, much less
     * jittery); falls back to accelerometer + magnetometer on devices without it.
     */
    fun heading(): Flow<HeadingSample> {
        val sm = sensorManager ?: return emptyFlow()
        val mag = magnetometer ?: return emptyFlow()
        val rv = rotationVector
        val acc = accelerometer
        if (rv == null && acc == null) return emptyFlow()

        return callbackFlow {
            val rotation = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasGeomagnetic = false
            var accuracy = SensorAccuracy.UNKNOWN

            fun emitFromRotationMatrix() {
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val azimuth =
                    CompassMath.normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
                trySend(HeadingSample(azimuth, accuracy))
            }

            fun tryFallback() {
                if (hasGravity && hasGeomagnetic &&
                    SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)
                ) {
                    emitFromRotationMatrix()
                }
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotation, event.values)
                            emitFromRotationMatrix()
                        }

                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            accuracy = SensorAccuracy.from(event.accuracy)
                            if (rv == null) {
                                lowPass(event.values, geomagnetic, 0.25f, hasGeomagnetic)
                                hasGeomagnetic = true
                                tryFallback()
                            }
                        }

                        Sensor.TYPE_ACCELEROMETER -> {
                            lowPass(event.values, gravity, 0.15f, hasGravity)
                            hasGravity = true
                            tryFallback()
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, newAccuracy: Int) {
                    if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        accuracy = SensorAccuracy.from(newAccuracy)
                    }
                }
            }

            // The magnetometer is always registered: it reports calibration accuracy,
            // which the fused rotation vector doesn't expose reliably.
            sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
            if (rv != null) {
                sm.registerListener(listener, rv, SensorManager.SENSOR_DELAY_GAME)
            } else {
                sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
            }
            awaitClose { sm.unregisterListener(listener) }
        }.conflate()
    }

    /** Gravity vector for the bubble level, rotated to match the current screen orientation. */
    fun gravity(): Flow<GravitySample> {
        val sm = sensorManager ?: return emptyFlow()
        val sensor = gravitySensor ?: accelerometer ?: return emptyFlow()
        val needsFilter = sensor.type == Sensor.TYPE_ACCELEROMETER

        return callbackFlow {
            val filtered = FloatArray(3)
            var initialised = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val v = if (needsFilter) {
                        lowPass(event.values, filtered, 0.12f, initialised)
                        initialised = true
                        filtered
                    } else {
                        event.values
                    }
                    val (sx, sy) = when (displayRotation()) {
                        Surface.ROTATION_90 -> -v[1] to v[0]
                        Surface.ROTATION_180 -> -v[0] to -v[1]
                        Surface.ROTATION_270 -> v[1] to -v[0]
                        else -> v[0] to v[1]
                    }
                    trySend(GravitySample(sx, sy, v[2]))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sm.unregisterListener(listener) }
        }.conflate()
    }

    /** Ambient light in lux. */
    fun light(): Flow<Float> {
        val sm = sensorManager ?: return emptyFlow()
        val sensor = lightSensor ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(event.values[0])
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sm.unregisterListener(listener) }
        }.conflate()
    }

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float, initialised: Boolean) {
        for (i in 0 until 3) {
            output[i] = if (initialised) output[i] + alpha * (input[i] - output[i]) else input[i]
        }
    }
}
