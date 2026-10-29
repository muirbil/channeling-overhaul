package com.equi.channelingoverhaul.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** [ChargeMath.discharge]: M from worn armor and water, D, and the flags of sound and death messages. */
class DischargeTest {
    private val p = ChargeParams()

    /** Armor bonuses per piece from DESIGN.md, a full set is 4 pieces. */
    private val sets = mapOf(
        "none" to emptyList(),
        "leather" to List(4) { 0.0 },
        "diamond" to List(4) { 0.0 },
        "iron" to List(4) { 0.10 },
        "chainmail" to List(4) { 0.10 },
        "netherite" to List(4) { 0.12 },
        "gold" to List(4) { 0.15 },
        "copper" to List(4) { 0.15 },
        "modded" to List(4) { 0.3 },
    )

    private fun discharge(q: Double, qMax: Double = 40.0, armor: List<Double> = emptyList(), wet: Boolean = false) =
        ChargeMath.discharge(q, qMax, armor, wet, p)

    @Test
    fun `below the threshold nothing happens`() {
        assertNull(discharge(2.99))
        assertNotNull(discharge(3.0))
    }

    @Test
    fun `full charge of Channeling V into a bare target deals 24 and spends all of it`() {
        val d = discharge(40.0)!!
        assertEquals(40.0, d.charge)
        assertEquals(1.0, d.vulnerability, 1e-9)
        assertEquals(24.0, d.damage, 1e-9)
    }

    @ParameterizedTest(name = "{0}, wet = {1}")
    @CsvSource(
        // armor set, wet, M
        "none,      false, 1.0",
        "none,      true,  1.25",
        "leather,   false, 1.0",
        "diamond,   true,  1.25",
        "iron,      false, 1.4",
        "chainmail, false, 1.4",
        "netherite, false, 1.48",
        "copper,    false, 1.6",
        "gold,      true,  1.85",
        "modded,    true,  2.0",
    )
    fun `M sums the armor pieces and water, D is 0_6 Q M`(set: String, wet: Boolean, m: Double) {
        val d = discharge(20.0, armor = sets.getValue(set), wet = wet)!!
        assertEquals(m, d.vulnerability, 1e-9)
        assertEquals(0.6 * 20.0 * m, d.damage, 1e-9)
    }

    @Test
    fun `a conductor wears a piece with a bonus, water alone is not one`() {
        assertTrue(discharge(10.0, armor = listOf(0.0, 0.10))!!.conductor)
        assertFalse(discharge(10.0, armor = sets.getValue("diamond"), wet = true)!!.conductor)
        assertFalse(discharge(10.0)!!.conductor)
    }

    @Test
    fun `strong from 50 percent of Qmax, overcharged from 90 percent`() {
        assertFalse(discharge(19.9)!!.strong)
        assertTrue(discharge(20.0)!!.strong)
        assertFalse(discharge(35.9)!!.overcharged)
        assertTrue(discharge(36.0)!!.overcharged)
    }

    @Test
    fun `horn volume goes from 1 to 5 with the charge`() {
        assertEquals(1.0, ChargeMath.hornVolume(0.0, 40.0, p), 1e-9)
        assertEquals(3.0, discharge(20.0)!!.hornVolume, 1e-9)
        assertEquals(5.0, discharge(40.0)!!.hornVolume, 1e-9)
        assertEquals(5.0, ChargeMath.hornVolume(50.0, 40.0, p), 1e-9)
    }
}
