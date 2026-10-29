package com.equi.channelingoverhaul.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Stage 8: spark rate from the inflow, glow stages, and when the server syncs a stuck trident. */
class VisualsTest {
    private val v = VisualParams()

    @ParameterizedTest(name = "I = {0}, intensity {1}: {2}/s")
    @CsvSource(
        "0,    1.0, 0",
        "0.1,  1.0, 0.6",
        "0.5,  1.0, 3",
        "1.5,  1.0, 9",
        "4.5,  1.0, 27",
        "6,    1.0, 30",
        "100,  1.0, 30",
        "1.5,  0.4, 3.6",
        "1.5,  0.1, 0.9",
        "-1,   1.0, 0",
    )
    fun `sparks follow the inflow up to a ceiling, scaled by the intensity`(inflow: Double, intensity: Double, perSecond: Double) {
        assertEquals(perSecond, Visuals.sparksPerSecond(inflow, intensity, v), 1e-9)
    }

    @Test
    fun `over a second the sparks of the ticks add up to the rate`() {
        // Rolls spread evenly over 0..1, as a random source does in the long run
        val perSecond = 9.0
        val total = (0 until 2000).sumOf { Visuals.sparksThisTick(perSecond, it / 2000.0) }
        assertEquals(perSecond / 20.0 * 2000, total.toDouble(), 1.0)
        assertEquals(0, Visuals.sparksThisTick(0.0, 0.999))
        assertEquals(2, Visuals.sparksThisTick(40.0, 0.0))
    }

    @ParameterizedTest(name = "Q = {0} of 40: stage {1}")
    @CsvSource("0, 0", "9.9, 0", "10, 1", "19.9, 1", "20, 2", "30, 3", "39.7, 3", "39.8, 4", "40, 4", "50, 4")
    fun `the glow steps up at 25, 50, 75 percent and at full`(charge: Double, stage: Int) {
        assertEquals(stage, Visuals.glowStage(Visuals.chargeFraction(charge, 40.0, v)))
    }

    @Test
    fun `no capacity, no glow`() {
        assertEquals(0.0, Visuals.chargeFraction(5.0, 0.0, v))
    }

    @Test
    fun `sync on first sight, a noticeable change or the full mark, not on every step`() {
        val station = StationView(charge = 20.0, qMax = 40.0, radius = 6.5, inflow = 1.5, antenna = 3.0)
        assertTrue(Visuals.needsSync(null, station, v))
        assertFalse(Visuals.needsSync(station, station.copy(charge = 20.39), v))
        assertTrue(Visuals.needsSync(station, station.copy(charge = 20.41), v))
        assertFalse(Visuals.needsSync(station, station.copy(inflow = 1.45), v))
        assertTrue(Visuals.needsSync(station, station.copy(inflow = 1.4), v))
        assertTrue(Visuals.needsSync(station, station.copy(inflow = 0.0), v))
        assertTrue(Visuals.needsSync(station, station.copy(qMax = 46.0, radius = 6.9), v))
        // 39.7 to 39.81 is a small step, but it crosses the full mark
        val nearlyFull = station.copy(charge = 39.7)
        assertTrue(Visuals.needsSync(nearlyFull, nearlyFull.copy(charge = 39.81), v))
    }
}
