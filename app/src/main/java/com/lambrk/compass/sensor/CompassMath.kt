package com.lambrk.compass.sensor

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/** Pure math helpers, kept free of Android types so they can be unit tested. */
object CompassMath {

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    private val CARDINAL_NAMES = arrayOf(
        "North", "Northeast", "East", "Southeast",
        "South", "Southwest", "West", "Northwest",
    )

    /** Wraps any angle into [0, 360). */
    fun normalize(degrees: Float): Float {
        val r = degrees % 360f
        return if (r < 0f) r + 360f else r
    }

    /** Signed shortest rotation from [from] to [to], in (-180, 180]. */
    fun shortestDelta(from: Float, to: Float): Float {
        var d = normalize(to) - normalize(from)
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Exponential low-pass filter on a circular quantity, so going from 359° to 1°
     * moves 2° instead of spinning 358° the other way.
     */
    fun smoothAngle(previous: Float?, next: Float, alpha: Float): Float {
        if (previous == null) return normalize(next)
        return normalize(previous + shortestDelta(previous, next) * alpha.coerceIn(0f, 1f))
    }

    /** Maps the 0..1 "smoothing" setting to a filter coefficient (1 = no smoothing). */
    fun smoothingToAlpha(smoothing: Float): Float = 1f - smoothing.coerceIn(0f, 1f) * 0.92f

    fun cardinal(degrees: Float): String = CARDINALS[cardinalIndex(degrees)]

    fun cardinalName(degrees: Float): String = CARDINAL_NAMES[cardinalIndex(degrees)]

    private fun cardinalIndex(degrees: Float): Int =
        (floor((normalize(degrees) + 22.5f) / 45f).toInt()) % 8

    /**
     * Tilt angles (degrees) from a gravity vector already expressed in screen coordinates.
     * Returns (x tilt / roll, y tilt / pitch). Both are 0 when the phone lies flat.
     */
    fun tiltDegrees(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1e-3f) return 0f to 0f
        val roll = Math.toDegrees(asin((x / g).coerceIn(-1f, 1f).toDouble())).toFloat()
        val pitch = Math.toDegrees(asin((y / g).coerceIn(-1f, 1f).toDouble())).toFloat()
        return roll to pitch
    }

    fun isLevel(roll: Float, pitch: Float, toleranceDegrees: Float = 1f): Boolean =
        abs(roll) <= toleranceDegrees && abs(pitch) <= toleranceDegrees

    /** Slider position (0..1) ↔ lux, on a log scale so dim values get real resolution. */
    fun luxToSlider(lux: Float, min: Float, max: Float): Float {
        val clamped = lux.coerceIn(min, max)
        return (ln(clamped / min) / ln(max / min)).coerceIn(0f, 1f)
    }

    fun sliderToLux(position: Float, min: Float, max: Float): Float =
        (min * exp(position.coerceIn(0f, 1f) * ln(max / min))).coerceIn(min, max)
}

/**
 * Decides light vs dark from ambient light readings. Uses hysteresis (the "back to light"
 * level is higher than the "go dark" level) and a hold time so the theme doesn't flicker
 * when the reading hovers near the threshold or a shadow briefly passes over the sensor.
 */
class AmbientThemeController(
    private val hysteresisRatio: Float = 1.6f,
    private val holdMillis: Long = 1_500L,
) {
    var isDark: Boolean? = null
        private set
    private var pendingSince: Long? = null

    fun reset() {
        isDark = null
        pendingSince = null
    }

    fun update(lux: Float, threshold: Float, nowMillis: Long): Boolean {
        val current = isDark
        if (current == null) {
            // First reading decides immediately.
            return (lux < threshold).also { isDark = it }
        }
        val wantsChange = if (current) lux > threshold * hysteresisRatio else lux < threshold
        if (!wantsChange) {
            pendingSince = null
            return current
        }
        val since = pendingSince ?: nowMillis.also { pendingSince = it }
        if (nowMillis - since >= holdMillis) {
            pendingSince = null
            isDark = !current
        }
        return isDark!!
    }
}

/**
 * Emits a tick when the heading crosses a cardinal direction (N/E/S/W). Requires the heading
 * to move [deadZone] degrees past the boundary, so jitter right on top of north doesn't buzz.
 */
class CardinalCrossingDetector(private val deadZone: Float = 3f) {
    private var sector: Int? = null

    fun onHeading(heading: Float): Boolean {
        val h = CompassMath.normalize(heading)
        val candidate = floor(h / 90f).toInt() % 4
        val current = sector
        if (current == null) {
            sector = candidate
            return false
        }
        if (candidate == current) return false
        val distanceToBoundary = minOf(h % 90f, 90f - h % 90f)
        if (distanceToBoundary < deadZone) return false
        sector = candidate
        return true
    }
}
