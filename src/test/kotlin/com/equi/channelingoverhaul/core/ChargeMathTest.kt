package com.equi.channelingoverhaul.core

import com.equi.channelingoverhaul.core.ChargeMath.capacity
import com.equi.channelingoverhaul.core.ChargeMath.charge
import com.equi.channelingoverhaul.core.ChargeMath.radius
import com.equi.channelingoverhaul.core.ChargeMath.skyInflow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.pow
import kotlin.math.sqrt

/** Checks the formulas, tables and examples of DESIGN.md against the default [ChargeParams]. */
class ChargeMathTest {
    private val p = ChargeParams()

    /** [n] conductors with the same proximity to the trident. */
    private fun ring(n: Int, conductivity: Double, proximity: Double, radius: Double) =
        List(n) { Conductor(radius * (1.0 - proximity), conductivity) }

    private fun antenna(copper: List<Conductor>, rods: List<Double>, water: List<Double>, radius: Double, p: ChargeParams) =
        ChargeMath.antenna(SourceScan(copper, rods, water), radius, p).total

    @Nested
    inner class LevelTable {
        @ParameterizedTest(name = "L = {0}")
        @CsvSource(
            // level, radius, capacity, full charge damage at M = 1, chain jumps
            "1,  4.0,  16,  9.6, 0",
            "5,  6.5,  40,  24,  1",
            "10, 8.3,  70,  42,  3",
            "20, 10.9, 130, 78,  6",
            "31, 13.1, 196, 118, 8",
        )
        fun `matches the table`(level: Int, r: Double, qMax: Double, damage: Double, jumps: Int) {
            assertEquals(r, radius(level, p), 0.05)
            assertEquals(qMax, capacity(level, p), 1e-9)
            assertEquals(damage, ChargeMath.dischargeDamage(capacity(level, p), 1.0, p), 0.5)
            assertEquals(jumps, ChargeMath.chainJumps(level, p))
        }

        @Test
        fun `radius is capped by config`() {
            assertEquals(16.0, radius(127, p), 1e-9)
            assertEquals(10.0, radius(31, p.copy(radiusMax = 10.0)), 1e-9)
        }

        @Test
        fun `XXXI in a thunderstorm under bare sky has a time constant of about 130 s`() {
            val tau = capacity(31, p) / skyInflow(true, Weather.THUNDER, Precipitation.RAIN, p)
            assertEquals(130.0, tau, 1.0)
        }
    }

    @Nested
    inner class Sky {
        @ParameterizedTest(name = "{0} + {1} = {2}")
        @CsvSource(
            "CLEAR,   RAIN, 0.1",
            "RAIN,    RAIN, 0.5",
            "THUNDER, RAIN, 1.5",
            "CLEAR,   SNOW, 0.1",
            "RAIN,    SNOW, 0.35",
            "THUNDER, SNOW, 1.0",
            "CLEAR,   NONE, 0.1",
            "RAIN,    NONE, 0.1",
            "THUNDER, NONE, 0.1",
        )
        fun `matches the table, biomes without precipitation count as clear`(
            weather: Weather,
            precipitation: Precipitation,
            expected: Double,
        ) {
            assertEquals(expected, skyInflow(true, weather, precipitation, p), 1e-9)
        }

        @Test
        fun `no open sky gives nothing`() {
            for (weather in Weather.entries) for (precipitation in Precipitation.entries) {
                assertEquals(0.0, skyInflow(false, weather, precipitation, p))
            }
        }
    }

    @Nested
    inner class Antenna {
        private val r = radius(5, p)

        @Test
        fun `bare sky is 1`() {
            assertEquals(1.0, antenna(emptyList(), emptyList(), emptyList(), r, p), 1e-9)
        }

        // The table assumes 16 blocks at an average proximity of 0.625 (sum of p = 10).
        // It rounds the second row, 1.75, down to 1.7.
        @ParameterizedTest(name = "c = {0}")
        @CsvSource("1.0, 2.0", "0.75, 1.75", "0.5, 1.5", "0.25, 1.25")
        fun `16 copper blocks match the oxidation table`(conductivity: Double, expected: Double) {
            assertEquals(expected, antenna(ring(16, conductivity, 0.625, r), emptyList(), emptyList(), r, p), 1e-9)
        }

        @Test
        fun `each next lightning rod gives half of the previous one`() {
            assertEquals(1.5, antenna(emptyList(), listOf(0.0), emptyList(), r, p), 1e-9)
            assertEquals(1.75, antenna(emptyList(), listOf(0.0, 0.0), emptyList(), r, p), 1e-9)
            assertEquals(1.875, antenna(emptyList(), listOf(0.0, 0.0, 0.0), emptyList(), r, p), 1e-9)
        }

        @Test
        fun `the closest rod gets the full weight whatever the scan order`() {
            val far = r * 0.5
            val expected = 1.0 + 0.5 * 1.0 + 0.25 * 0.5
            assertEquals(expected, antenna(emptyList(), listOf(far, 0.0), emptyList(), r, p), 1e-9)
        }

        @Test
        fun `water gives at most +0_3`() {
            assertEquals(1.3, antenna(emptyList(), emptyList(), List(100) { 0.0 }, r, p), 1e-9)
        }

        @Test
        fun `blocks on the edge and outside the radius give nothing`() {
            val outside = ring(10, 1.0, 0.0, r) + Conductor(r + 3.0, 1.0)
            assertEquals(1.0, antenna(outside, listOf(r, r + 1.0), listOf(r + 2.0), r, p), 1e-9)
        }

        @Test
        fun `is capped at 3`() {
            assertEquals(3.0, antenna(ring(100, 1.0, 1.0, r), emptyList(), emptyList(), r, p), 1e-9)
        }
    }

    @Nested
    inner class Charging {
        @Test
        fun `follows the saturation formula`() {
            val qMax = 40.0
            assertEquals(qMax * (1 - kotlin.math.exp(-0.5 * 60 / qMax)), charge(0.0, 0.5, 60.0, qMax), 1e-9)
        }

        @Test
        fun `steps do not accumulate error`() {
            val once = charge(5.0, 1.2, 60.0, 40.0)
            var stepped = 5.0
            repeat(120) { stepped = charge(stepped, 1.2, 0.5, 40.0) }
            assertEquals(once, stepped, 1e-9)
        }

        @Test
        fun `never goes above capacity`() {
            assertTrue(charge(39.0, 1_000.0, 3_600.0, 40.0) <= 40.0)
        }

        @Test
        fun `no inflow keeps the charge`() {
            assertEquals(12.0, charge(12.0, 0.0, 60.0, 40.0))
        }

        // Examples for Channeling V after 60 s. The document rounds charge and damage to whole numbers.
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            // conditions, weather, copper blocks, conductivity, charge, damage at M = 1
            "clear without copper,          CLEAR,   0,  0.0,  6,  3",
            "rain without copper,           RAIN,    0,  0.0,  21, 13",
            "rain with 16 oxidized copper,  RAIN,    16, 0.25, 24, 14",
            "rain with 16 pure copper,      RAIN,    16, 1.0,  31, 18",
            "thunder without copper,        THUNDER, 0,  0.0,  36, 21",
        )
        fun `channeling V examples`(
            name: String,
            weather: Weather,
            blocks: Int,
            conductivity: Double,
            expectedCharge: Double,
            expectedDamage: Double,
        ) {
            val r = radius(5, p)
            val a = antenna(ring(blocks, conductivity, 0.625, r), emptyList(), emptyList(), r, p)
            val inflow = ChargeMath.inflow(skyInflow(true, weather, Precipitation.RAIN, p), a)
            val q = charge(0.0, inflow, 60.0, capacity(5, p))
            assertEquals(expectedCharge, q, 0.5)
            assertEquals(expectedDamage, ChargeMath.dischargeDamage(q, 1.0, p), 1.0)
        }
    }

    @Nested
    inner class Station {
        // The station from DESIGN.md, top view, one cell per block. The platform lies at y = 0,
        // the rod and the lanterns stand on it at y = 1.
        private val map = listOf(
            "L # # # # L",
            "# # # # # #",
            "# # # # # #",
            "# # # # # #",
            "# # x # R #",
            "L # # # # L",
        )

        private fun stationAntenna(level: Int, lanterns: Boolean): Double {
            val cells = map.map { it.split(' ') }
            val z0 = cells.indexOfFirst { "x" in it }
            val x0 = cells[z0].indexOf("x")
            val r = radius(level, p)
            // A trident stuck in the top of a block sits 0.05 above it.
            fun distance(x: Int, y: Int, z: Int) =
                sqrt(((x - x0) * (x - x0) + (z - z0) * (z - z0)).toDouble() + (y + 0.5 - 1.05).pow(2))
            val copper = mutableListOf<Conductor>()
            val rods = mutableListOf<Double>()
            for ((z, row) in cells.withIndex()) for ((x, cell) in row.withIndex()) {
                copper += Conductor(distance(x, 0, z), 1.0)
                when (cell) {
                    "R" -> rods += distance(x, 1, z)
                    "L" -> if (lanterns) copper += Conductor(distance(x, 1, z), 1.0)
                }
            }
            return antenna(copper.filter { it.distance <= r }, rods.filter { it <= r }, emptyList(), r, p)
        }

        /** Seconds to 95% Qmax from empty, counted in the 10-tick steps of the charger. */
        private fun timeTo95(level: Int, weather: Weather, antenna: Double): Double {
            val qMax = capacity(level, p)
            val inflow = ChargeMath.inflow(skyInflow(true, weather, Precipitation.RAIN, p), antenna)
            var t = 0.0
            while (charge(0.0, inflow, t, qMax) < 0.95 * qMax) t += 0.5
            return t
        }

        @ParameterizedTest(name = "L = {0}, lanterns = {1}")
        @CsvSource(
            // level, lanterns, antenna, seconds to 95% in rain, in a thunderstorm
            "1,  false, 2.47, 39,  13",
            "1,  true,  2.54, 38,  13",
            "5,  false, 3.0,  80,  27",
            "10, false, 3.0,  140, 47",
            "20, false, 3.0,  260, 87",
        )
        fun `matches the table`(level: Int, lanterns: Boolean, a: Double, rain: Double, thunder: Double) {
            val antenna = stationAntenna(level, lanterns)
            assertEquals(a, antenna, 0.005)
            assertEquals(rain, timeTo95(level, Weather.RAIN, antenna))
            assertEquals(thunder, timeTo95(level, Weather.THUNDER, antenna))
        }

        @Test
        fun `bare sky takes three times as long`() {
            assertEquals(240.0, timeTo95(5, Weather.RAIN, 1.0))
            assertEquals(779.0, timeTo95(20, Weather.RAIN, 1.0))
        }
    }

    @Nested
    inner class Leak {
        @Test
        fun `halves every 150 s`() {
            assertEquals(20.0, ChargeMath.leak(40.0, 150.0, p), 1e-9)
            assertEquals(10.0, ChargeMath.leak(40.0, 300.0, p), 1e-9)
            assertEquals(20.0, ChargeMath.leak(40.0, ChargeMath.ticksToSeconds(3_000), p), 1e-9)
        }

        @Test
        fun `lazy leak over a long gap equals leaking step by step`() {
            var stepped = 40.0
            repeat(30) { stepped = ChargeMath.leak(stepped, 10.0, p) }
            assertEquals(ChargeMath.leak(40.0, 300.0, p), stepped, 1e-9)
        }
    }

    @Nested
    inner class Strike {
        @Test
        fun `a strike into a rod gives +50 percent, elsewhere +25 percent`() {
            assertEquals(20.0, ChargeMath.strike(0.0, 40.0, intoRod = true, p))
            assertEquals(10.0, ChargeMath.strike(0.0, 40.0, intoRod = false, p))
        }

        @Test
        fun `does not overflow capacity`() {
            assertEquals(40.0, ChargeMath.strike(30.0, 40.0, intoRod = true, p))
        }
    }

    @Nested
    inner class Windup {
        @Test
        fun `uses half of the inflow`() {
            assertEquals(charge(0.0, 0.25, 5.0, 40.0), ChargeMath.windup(0.0, 0.5, 5.0, 40.0, p), 1e-9)
        }

        @Test
        fun `stops at 30 percent of capacity`() {
            assertEquals(12.0, ChargeMath.windup(0.0, 1_000.0, 60.0, 40.0, p), 1e-9)
        }

        @Test
        fun `adds nothing above the cap`() {
            assertEquals(30.0, ChargeMath.windup(30.0, 1_000.0, 60.0, 40.0, p))
        }
    }

    @Nested
    inner class Discharge {
        @Test
        fun `needs at least 3 charge`() {
            assertFalse(ChargeMath.canDischarge(2.99, p))
            assertTrue(ChargeMath.canDischarge(3.0, p))
        }

        @Test
        fun `damage is 0_6 Q M`() {
            assertEquals(0.6 * 25 * 1.4, ChargeMath.dischargeDamage(25.0, 1.4, p), 1e-9)
        }

        @Test
        fun `vulnerability examples`() {
            assertEquals(1.0, ChargeMath.vulnerability(0.0, wet = false, p), 1e-9)
            // full iron set
            assertEquals(1.4, ChargeMath.vulnerability(4 * 0.10, wet = false, p), 1e-9)
            // full gold set on a wet target
            assertEquals(1.85, ChargeMath.vulnerability(4 * 0.15, wet = true, p), 1e-9)
            // capped at 2
            assertEquals(2.0, ChargeMath.vulnerability(4 * 0.15 + 1.0, wet = true, p), 1e-9)
        }

        @Test
        fun `strong discharge from 50 percent, overcharged from 90 percent`() {
            assertFalse(ChargeMath.isStrongDischarge(19.9, 40.0, p))
            assertTrue(ChargeMath.isStrongDischarge(20.0, 40.0, p))
            assertFalse(ChargeMath.isOvercharged(35.9, 40.0, p))
            assertTrue(ChargeMath.isOvercharged(36.0, 40.0, p))
        }
    }

    @Nested
    inner class Chain {
        @Test
        fun `example 24, 14, 9, 5`() {
            // Q = 40 gives D = 24 at M = 1
            val hops = (0..3).map { Math.round(ChargeMath.chainDamage(40.0, it, 1.0, p)).toInt() }
            assertEquals(listOf(24, 14, 9, 5), hops)
        }

        @Test
        fun `M of a link applies only to that link`() {
            assertEquals(24.0 * 0.6 * 0.6 * 2.0, ChargeMath.chainDamage(40.0, 2, 2.0, p), 1e-9)
        }

        @Test
        fun `a chain of metal-clad targets does not grow`() {
            val hops = (0..8).map { ChargeMath.chainDamage(40.0, it, 2.0, p) }
            assertEquals(hops.sortedDescending(), hops)
        }

        @Test
        fun `conductive targets are reached from farther`() {
            assertEquals(4.0, ChargeMath.chainRange(1.0, p))
            assertEquals(6.0, ChargeMath.chainRange(1.1, p))
        }

        @Test
        fun `jumps are capped at 8`() {
            assertEquals(8, ChargeMath.chainJumps(127, p))
            assertEquals(0, ChargeMath.chainJumps(2, p))
        }
    }

    @Nested
    inner class WaterSpread {
        @ParameterizedTest(name = "L = {0}")
        @CsvSource("1, 3.2", "5, 4.0", "10, 5.0", "25, 8.0", "31, 8.0")
        fun `radius is min(8, 3 + L div 5)`(level: Int, expected: Double) {
            assertEquals(expected, ChargeMath.spreadRadius(level, p), 1e-9)
        }

        @Test
        fun `damage falls off with distance`() {
            val r = ChargeMath.spreadRadius(10, p)
            assertEquals(0.4 * 24 * 1.25, ChargeMath.spreadDamage(40.0, 0.0, r, 1.25, p), 1e-9)
            assertEquals(0.4 * 24 * 0.5, ChargeMath.spreadDamage(40.0, r / 2, r, 1.0, p), 1e-9)
            assertEquals(0.0, ChargeMath.spreadDamage(40.0, r, r, 1.0, p), 1e-9)
            assertEquals(0.0, ChargeMath.spreadDamage(40.0, r + 2, r, 1.0, p), 1e-9)
        }
    }
}
