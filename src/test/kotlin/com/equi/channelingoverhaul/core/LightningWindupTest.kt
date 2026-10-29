package com.equi.channelingoverhaul.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Stage 7: the bonus of a lightning strike near a stuck trident and the windup cap. */
class LightningWindupTest {
    private val p = ChargeParams()

    @ParameterizedTest(name = "Q = {0} of {1}, rod = {2}: {3}")
    @CsvSource(
        // charge, Qmax, into a rod, after
        "0,   16, false, 4",
        "0,   16, true,  8",
        "10,  40, false, 20",
        "10,  40, true,  30",
        "14,  16, true,  16",
        "13,  16, false, 16",
        "16,  16, true,  16",
        "100, 196, true, 196",
    )
    fun `a strike adds 25 percent of Qmax, 50 into a rod, never above Qmax`(q: Double, qMax: Double, intoRod: Boolean, after: Double) {
        assertEquals(after, ChargeMath.strike(q, qMax, intoRod, p), 1e-9)
    }

    @Test
    fun `windup charges with half the inflow`() {
        // Rain under bare sky: I = 0.5, the windup uses 0.25
        assertEquals(ChargeMath.charge(2.0, 0.25, 2.0, 16.0), ChargeMath.windup(2.0, 0.5, 2.0, 16.0, p), 1e-12)
    }

    @Test
    fun `windup stops at 30 percent of Qmax, however long`() {
        assertEquals(4.8, ChargeMath.windup(4.7, 0.5, 2.0, 16.0, p), 1e-12)
        assertEquals(12.0, ChargeMath.windup(0.0, 1.5 * 3.0, 3600.0, 40.0, p), 1e-12)
        // Step by step it never passes the cap either
        var q = 0.0
        repeat(20 * 600) { q = ChargeMath.windup(q, 4.5, 0.05, 40.0, p) }
        assertEquals(12.0, q, 1e-9)
    }

    @Test
    fun `windup adds nothing at or above the cap, nor without inflow`() {
        assertEquals(4.8, ChargeMath.windup(4.8, 4.5, 10.0, 16.0, p), 1e-12)
        assertEquals(6.0, ChargeMath.windup(6.0, 4.5, 10.0, 16.0, p), 1e-12)
        assertEquals(2.0, ChargeMath.windup(2.0, 0.0, 10.0, 16.0, p), 1e-12)
    }
}
