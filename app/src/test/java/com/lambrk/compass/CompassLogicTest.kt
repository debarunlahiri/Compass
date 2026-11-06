package com.lambrk.compass

import com.lambrk.compass.sensor.AmbientThemeController
import com.lambrk.compass.sensor.CardinalCrossingDetector
import com.lambrk.compass.sensor.CompassMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassMathTest {

    @Test
    fun normalize_wrapsIntoRange() {
        assertEquals(0f, CompassMath.normalize(360f), 1e-4f)
        assertEquals(350f, CompassMath.normalize(-10f), 1e-4f)
        assertEquals(10f, CompassMath.normalize(730f), 1e-4f)
    }

    @Test
    fun shortestDelta_crossesNorthTheShortWay() {
        assertEquals(2f, CompassMath.shortestDelta(359f, 1f), 1e-4f)
        assertEquals(-2f, CompassMath.shortestDelta(1f, 359f), 1e-4f)
        assertEquals(90f, CompassMath.shortestDelta(0f, 90f), 1e-4f)
    }

    @Test
    fun smoothAngle_doesNotSpinAroundAtNorth() {
        val result = CompassMath.smoothAngle(previous = 358f, next = 2f, alpha = 0.5f)
        assertEquals(0f, result, 1e-3f)
    }

    @Test
    fun smoothAngle_firstSampleIsTakenAsIs() {
        assertEquals(123f, CompassMath.smoothAngle(null, 123f, 0.1f), 1e-4f)
    }

    @Test
    fun smoothingToAlpha_zeroMeansRaw() {
        assertEquals(1f, CompassMath.smoothingToAlpha(0f), 1e-4f)
        assertTrue(CompassMath.smoothingToAlpha(1f) < 0.2f)
    }

    @Test
    fun cardinal_labels() {
        assertEquals("N", CompassMath.cardinal(0f))
        assertEquals("N", CompassMath.cardinal(359f))
        assertEquals("NE", CompassMath.cardinal(45f))
        assertEquals("E", CompassMath.cardinal(90f))
        assertEquals("S", CompassMath.cardinal(180f))
        assertEquals("W", CompassMath.cardinal(270f))
        assertEquals("NW", CompassMath.cardinal(315f))
        assertEquals("West", CompassMath.cardinalName(272f))
    }

    @Test
    fun tilt_flatPhoneIsLevel() {
        val (roll, pitch) = CompassMath.tiltDegrees(0f, 0f, 9.81f)
        assertEquals(0f, roll, 1e-3f)
        assertEquals(0f, pitch, 1e-3f)
        assertTrue(CompassMath.isLevel(roll, pitch))
    }

    @Test
    fun tilt_rightEdgeRaisedGivesPositiveRoll() {
        // 30° tilt: x = g·sin30, z = g·cos30
        val (roll, pitch) = CompassMath.tiltDegrees(4.905f, 0f, 8.496f)
        assertEquals(30f, roll, 0.1f)
        assertEquals(0f, pitch, 0.1f)
        assertFalse(CompassMath.isLevel(roll, pitch))
    }

    @Test
    fun luxSlider_roundTrips() {
        for (lux in listOf(1f, 5f, 20f, 150f, 1000f)) {
            val pos = CompassMath.luxToSlider(lux, 1f, 1000f)
            assertEquals(lux, CompassMath.sliderToLux(pos, 1f, 1000f), lux * 0.001f)
        }
        assertEquals(0f, CompassMath.luxToSlider(0.1f, 1f, 1000f), 1e-4f)
        assertEquals(1f, CompassMath.luxToSlider(5000f, 1f, 1000f), 1e-4f)
    }
}

class AmbientThemeControllerTest {

    @Test
    fun firstReadingDecidesImmediately() {
        assertTrue(AmbientThemeController().update(lux = 3f, threshold = 20f, nowMillis = 0))
        assertFalse(AmbientThemeController().update(lux = 300f, threshold = 20f, nowMillis = 0))
    }

    @Test
    fun goesDarkOnlyAfterHoldTime() {
        val c = AmbientThemeController(holdMillis = 1_000)
        assertFalse(c.update(300f, 20f, 0))
        assertFalse(c.update(5f, 20f, 100)) // starts pending
        assertFalse(c.update(5f, 20f, 900))
        assertTrue(c.update(5f, 20f, 1_100))
    }

    @Test
    fun briefShadowDoesNotSwitch() {
        val c = AmbientThemeController(holdMillis = 1_000)
        c.update(300f, 20f, 0)
        c.update(5f, 20f, 100)
        assertFalse(c.update(300f, 20f, 500)) // light again → pending cancelled
        assertFalse(c.update(5f, 20f, 1_200)) // new pending starts here
        assertFalse(c.update(5f, 20f, 2_000))
        assertTrue(c.update(5f, 20f, 2_300))
    }

    @Test
    fun hysteresisKeepsDarkNearThreshold() {
        val c = AmbientThemeController(hysteresisRatio = 1.6f, holdMillis = 0)
        assertTrue(c.update(5f, 20f, 0))
        // 25 lx is above the dark threshold but below 20 × 1.6 = 32 → stays dark.
        assertTrue(c.update(25f, 20f, 10))
        assertTrue(c.update(25f, 20f, 5_000))
        // 40 lx clears the hysteresis band → back to light.
        assertFalse(c.update(40f, 20f, 5_010))
    }
}

class CardinalCrossingDetectorTest {

    @Test
    fun ticksWhenPassingEast() {
        val d = CardinalCrossingDetector(deadZone = 3f)
        assertFalse(d.onHeading(80f))
        assertFalse(d.onHeading(89f))
        assertFalse(d.onHeading(91f)) // inside dead zone
        assertTrue(d.onHeading(94f))
        assertFalse(d.onHeading(100f))
    }

    @Test
    fun jitterAroundNorthDoesNotBuzz() {
        val d = CardinalCrossingDetector(deadZone = 3f)
        d.onHeading(5f)
        for (h in listOf(359f, 1f, 358.5f, 1.5f, 359.5f)) {
            assertFalse(d.onHeading(h))
        }
    }
}
