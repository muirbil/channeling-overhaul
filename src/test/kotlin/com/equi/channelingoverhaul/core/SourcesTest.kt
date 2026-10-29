package com.equi.channelingoverhaul.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Sources collected by a scan and the antenna they give, without Minecraft. */
class SourcesTest {
    private val p = ChargeParams()
    private val r = ChargeMath.radius(5, p)

    private val copper = Conductivity(ConductorType.COPPER, 0.5)
    private val rod = Conductivity(ConductorType.LIGHTNING_ROD)
    private val water = Conductivity(ConductorType.WATER)

    @Test
    fun `scan sorts blocks by kind and keeps copper conductivity`() {
        val scan = SourceScan.Builder().apply {
            add(1.0, copper, isFluidSource = false)
            add(2.0, rod, isFluidSource = false)
            add(3.0, water, isFluidSource = true)
        }.build()
        assertEquals(SourceScan(listOf(Conductor(1.0, 0.5)), listOf(2.0), listOf(3.0)), scan)
    }

    @Test
    fun `only source water counts, flowing water does not`() {
        val scan = SourceScan.Builder().apply {
            add(1.0, water, isFluidSource = true)
            add(1.0, water, isFluidSource = false)
        }.build()
        assertEquals(listOf(1.0), scan.water)
    }

    @Test
    fun `lightning rods ignore conductivity`() {
        val scan = SourceScan.Builder().apply { add(0.0, Conductivity(ConductorType.LIGHTNING_ROD, 0.25), false) }.build()
        assertEquals(0.5, ChargeMath.antenna(scan, r, p).rods, 1e-9)
    }

    @Test
    fun `breakdown adds up to the total below the cap`() {
        val scan = SourceScan(
            copper = listOf(Conductor(0.0, 1.0), Conductor(r / 2, 0.5)),
            rods = listOf(r / 2, 0.0),
            water = listOf(0.0, r / 4),
        )
        val a = ChargeMath.antenna(scan, r, p)
        assertEquals(0.1 * 1.0 * 1.0 + 0.1 * 0.5 * 0.5, a.copper, 1e-9)
        // the closer rod comes first whatever the scan order: 0.5^0·0.5·1 + 0.5^1·0.5·0.5
        assertEquals(0.5 * 1.0 + 0.5 * 0.5 * 0.5, a.rods, 1e-9)
        assertEquals(0.02 * 1.0 + 0.02 * 0.75, a.water, 1e-9)
        assertEquals(1.0 + a.copper + a.rods + a.water, a.total, 1e-9)
    }

    @Test
    fun `water part is capped at 0_3, the total at 3, the other parts are not`() {
        val scan = SourceScan(copper = List(40) { Conductor(0.0, 1.0) }, water = List(100) { 0.0 })
        val a = ChargeMath.antenna(scan, r, p)
        assertEquals(4.0, a.copper, 1e-9)
        assertEquals(0.3, a.water, 1e-9)
        assertEquals(3.0, a.total, 1e-9)
    }

    @Test
    fun `inflow is sky times antenna, zero without open sky`() {
        val scan = SourceScan(copper = List(16) { Conductor(r * 0.375, 1.0) })
        val a = ChargeMath.antenna(scan, r, p).total
        assertEquals(0.5 * 2.0, ChargeMath.inflow(ChargeMath.skyInflow(true, Weather.RAIN, Precipitation.RAIN, p), a), 1e-9)
        assertEquals(0.0, ChargeMath.inflow(ChargeMath.skyInflow(false, Weather.RAIN, Precipitation.RAIN, p), a))
    }
}
