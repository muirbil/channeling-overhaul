package com.equi.channelingoverhaul.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Chain hops and water spread of stage 6: falloff, hop count, picking the next link, water radius and damage. */
class ChainTest {
    private val p = ChargeParams()

    private fun candidate(distance: Double, m: Double = 1.0) = ChainCandidate(distance, m)

    @Test
    fun `each base is 0_6 of the previous one, from 0_6 Q`() {
        val bases = (0..4).map { ChargeMath.chainBase(40.0, it, p) }
        val expected = listOf(24.0, 14.4, 8.64, 5.184, 3.1104)
        expected.zip(bases).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
    }

    @Test
    fun `the target's M scales only its own hop`() {
        // A gold-clad wet link (M = 1.85) between two bare ones
        val damages = listOf(1.0, 1.85, 1.0).mapIndexed { hop, m -> ChargeMath.chainDamage(40.0, hop + 1, m, p) }
        assertEquals(14.4, damages[0], 1e-9)
        assertEquals(8.64 * 1.85, damages[1], 1e-9)
        assertEquals(5.184, damages[2], 1e-9)
    }

    @ParameterizedTest(name = "L = {0}: {1} hops")
    @CsvSource("0, 0", "1, 0", "2, 0", "3, 1", "5, 1", "6, 2", "9, 3", "10, 3", "20, 6", "23, 7", "24, 8", "31, 8", "127, 8")
    fun `hops are floor(L div 3), at most 8`(level: Int, hops: Int) {
        assertEquals(hops, ChargeMath.chainJumps(level, p))
    }

    @Test
    fun `the next link is the closest one within 4 blocks`() {
        assertEquals(1, ChargeMath.nextLink(listOf(candidate(3.5), candidate(2.0), candidate(3.0)), p))
        assertEquals(0, ChargeMath.nextLink(listOf(candidate(4.0)), p))
        assertNull(ChargeMath.nextLink(listOf(candidate(4.01), candidate(5.9)), p))
        assertNull(ChargeMath.nextLink(emptyList(), p))
    }

    @Test
    fun `a conductive candidate is reached from 6 blocks, a plain one is not`() {
        val plainAt5 = candidate(5.0)
        val ironAt6 = candidate(6.0, m = 1.4)
        val wetAt7 = candidate(7.0, m = 1.25)
        assertEquals(1, ChargeMath.nextLink(listOf(plainAt5, ironAt6, wetAt7), p))
        // Still the closest one wins when several are in range
        assertEquals(1, ChargeMath.nextLink(listOf(ironAt6, candidate(3.9)), p))
    }

    @ParameterizedTest(name = "L = {0}: r_w = {1}")
    @CsvSource("1, 3.2", "5, 4.0", "10, 5.0", "20, 7.0", "25, 8.0", "31, 8.0")
    fun `water radius is min(8, 3 + L div 5)`(level: Int, radius: Double) {
        assertEquals(radius, ChargeMath.spreadRadius(level, p), 1e-9)
    }

    @Test
    fun `water damage is 0_4 of the base 0_6 Q, times (1 - d div r_w) and the target's M`() {
        val q = 40.0
        assertEquals(0.4 * 24.0, ChargeMath.spreadDamage(q, 0.0, 4.0, 1.0, p), 1e-9)
        assertEquals(0.2 * 24.0 * 1.25, ChargeMath.spreadDamage(q, 2.0, 4.0, 1.25, p), 1e-9)
        assertEquals(0.2 * 24.0, ChargeMath.spreadBase(q, 2.0, 4.0, p), 1e-9)
        assertEquals(0.0, ChargeMath.spreadDamage(q, 4.0, 4.0, 2.0, p), 1e-9)
        assertEquals(0.0, ChargeMath.spreadDamage(q, 6.0, 4.0, 2.0, p), 1e-9)
    }

    @Test
    fun `the primary target's M does not reach the water`() {
        // Primary target in a wet gold set (M = 1.85) or bare: the water hit on a wet target is the same
        val primary = ChargeMath.discharge(40.0, 40.0, List(4) { 0.15 }, wet = true, p)!!
        assertEquals(1.85, primary.vulnerability, 1e-9)
        assertEquals(0.4 * 24.0 * 1.25, ChargeMath.spreadDamage(primary.charge, 0.0, 4.0, 1.25, p), 1e-9)
    }
}
