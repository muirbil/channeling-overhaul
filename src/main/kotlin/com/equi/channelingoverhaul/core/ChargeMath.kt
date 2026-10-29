package com.equi.channelingoverhaul.core

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Weather at the trident. Thunder implies rain, as in vanilla. */
enum class Weather { CLEAR, RAIN, THUNDER }

/** What the biome drops at the trident position (`Biome.getPrecipitationAt`). */
enum class Precipitation { NONE, RAIN, SNOW }

/** What a hit of a thrown trident does with its charge, see [ChargeMath.discharge]. */
data class Discharge(
    /** Q, all of it is spent */
    val charge: Double,
    /** M of the target */
    val vulnerability: Double,
    /** D = 0.6·Q·M */
    val damage: Double,
    /** The target wears armor with a bonus above 0: the "metal conducts electricity" death message */
    val conductor: Boolean,
    /** Q ≥ 50% Qmax: visual lightning and the horn, otherwise only a quiet crackle */
    val strong: Boolean,
    /** Q ≥ 90% Qmax: the "vaporized" death message */
    val overcharged: Boolean,
    /** 1 + 4·Q/Qmax */
    val hornVolume: Double,
)

/** A possible next link of the chain: its distance from the previous link and its M. */
data class ChainCandidate(val distance: Double, val vulnerability: Double)

/**
 * All game math of the mod as pure functions, without Minecraft classes,
 * so balance can be unit tested. Formulas are described in DESIGN.md.
 */
object ChargeMath {
    const val TICKS_PER_SECOND = 20.0

    fun ticksToSeconds(ticks: Long): Double = ticks / TICKS_PER_SECOND

    /** Qmax(L) = 10 + 6·L */
    fun capacity(level: Int, p: ChargeParams): Double =
        p.capacityBase + p.capacityPerLevel * level

    /** R(L) = 2 + 2·√L, capped by [ChargeParams.radiusMax]. */
    fun radius(level: Int, p: ChargeParams): Double =
        min(p.radiusMax, p.radiusBase + p.radiusPerSqrtLevel * sqrt(level.coerceAtLeast(0).toDouble()))

    /** p = 1 − d / R: 1 at the trident, 0 at the edge of the radius and beyond. */
    fun proximity(distance: Double, radius: Double): Double =
        if (radius <= 0.0) 0.0 else (1.0 - distance / radius).coerceIn(0.0, 1.0)

    /** Sky inflow S. Biomes without precipitation count as clear in any weather. */
    fun skyInflow(canSeeSky: Boolean, weather: Weather, precipitation: Precipitation, p: ChargeParams): Double {
        if (!canSeeSky) return 0.0
        if (precipitation == Precipitation.NONE) return p.skyClear
        val snow = precipitation == Precipitation.SNOW
        return when (weather) {
            Weather.CLEAR -> p.skyClear
            Weather.RAIN -> if (snow) p.skySnow else p.skyRain
            Weather.THUNDER -> if (snow) p.skyThunderSnow else p.skyThunderRain
        }
    }

    /**
     * Antenna multiplier:
     * A = min(3, 1 + Σ_copper 0.1·c·p + Σ_k 0.5^k·0.5·p_k + min(0.3, Σ_water 0.02·p)),
     * where k numbers lightning rods from the closest one.
     */
    fun antenna(scan: SourceScan, radius: Double, p: ChargeParams): Antenna {
        val copper = scan.copper.sumOf { p.copperWeight * it.conductivity * proximity(it.distance, radius) }
        val rods = scan.rods.sorted().withIndex().sumOf { (k, d) ->
            p.rodFalloff.pow(k) * p.rodWeight * proximity(d, radius)
        }
        val water = min(p.antennaWaterMax, scan.water.sumOf { p.antennaWaterWeight * proximity(it, radius) })
        return Antenna(copper, rods, water, min(p.antennaMax, 1.0 + copper + rods + water))
    }

    /** I = S·A */
    fun inflow(sky: Double, antenna: Double): Double = sky * antenna

    /**
     * Saturating charge over [seconds]: Q ← Qmax − (Qmax − Q)·exp(−I·t / Qmax).
     * Exact for any step, so splitting the time into steps gives the same result.
     */
    fun charge(q: Double, inflow: Double, seconds: Double, qMax: Double): Double {
        if (inflow <= 0.0 || qMax <= 0.0) return q
        return qMax - (qMax - q) * exp(-inflow * seconds / qMax)
    }

    /** Leak outside charging: Q ← Q·2^(−t / T½). */
    fun leak(q: Double, seconds: Double, p: ChargeParams): Double =
        q * 2.0.pow(-seconds / p.leakHalfLife)

    /** One-off bonus from a lightning strike within the radius: +50% Qmax into a rod, +25% elsewhere. */
    fun strike(q: Double, qMax: Double, intoRod: Boolean, p: ChargeParams): Double {
        val bonus = if (intoRod) p.strikeRodBonus else p.strikeNearbyBonus
        return min(qMax, q + bonus * qMax).coerceAtLeast(q)
    }

    /**
     * Windup recharge: inflow at the player scaled by [ChargeParams.windupFactor],
     * never above [ChargeParams.windupCap] of Qmax. A charge already above the cap is left as is.
     */
    fun windup(q: Double, inflow: Double, seconds: Double, qMax: Double, p: ChargeParams): Double {
        val cap = p.windupCap * qMax
        if (q >= cap) return q
        return min(cap, charge(q, inflow * p.windupFactor, seconds, qMax))
    }

    /** A hit on a mob discharges only from this charge on; below it the charge stays in the trident. */
    fun canDischarge(q: Double, p: ChargeParams): Boolean = q >= p.minDischargeCharge

    /** M = min(2, 1 + Σ armor bonuses + wet bonus). Armor bonuses come from the `armor_conductivity` data map. */
    fun vulnerability(armorBonus: Double, wet: Boolean, p: ChargeParams): Double {
        val m = 1.0 + armorBonus + if (wet) p.wetBonus else 0.0
        return m.coerceIn(0.0, p.vulnerabilityMax)
    }

    /** Damage to the primary target: D = 0.6·Q·M */
    fun dischargeDamage(q: Double, m: Double, p: ChargeParams): Double = p.damagePerCharge * q * m

    /**
     * A thrown trident with charge [q] hits a target whose worn armor pieces have [armorBonuses]
     * (`armor_conductivity`). Null below the threshold: nothing happens and the charge stays.
     */
    fun discharge(q: Double, qMax: Double, armorBonuses: List<Double>, wet: Boolean, p: ChargeParams): Discharge? {
        if (!canDischarge(q, p)) return null
        val m = vulnerability(armorBonuses.sum(), wet, p)
        return Discharge(
            charge = q,
            vulnerability = m,
            damage = dischargeDamage(q, m, p),
            conductor = armorBonuses.any { it > 0.0 },
            strong = isStrongDischarge(q, qMax, p),
            overcharged = isOvercharged(q, qMax, p),
            hornVolume = hornVolume(q, qMax, p),
        )
    }

    /** Horn volume 1 + 4·Q/Qmax: heard from 16 blocks at no charge to 80 at full, like the vanilla horn at 5. */
    fun hornVolume(q: Double, qMax: Double, p: ChargeParams): Double =
        p.hornVolumeBase + p.hornVolumePerCharge * if (qMax > 0.0) (q / qMax).coerceIn(0.0, 1.0) else 0.0

    /** N(L) = min(8, ⌊L / 3⌋) */
    fun chainJumps(level: Int, p: ChargeParams): Int =
        min(p.chainMaxJumps, level.coerceAtLeast(0) / p.chainLevelsPerJump)

    /** Search range for the next link: farther for conductive targets (M > 1). */
    fun chainRange(m: Double, p: ChargeParams): Double =
        if (m > 1.0) p.chainConductiveRange else p.chainRange

    /** Base of chain hop [hop] before the target's M: 0.6·Q·0.6^hop, hop 0 being the primary target. */
    fun chainBase(q: Double, hop: Int, p: ChargeParams): Double =
        dischargeDamage(q, 1.0, p) * p.chainFalloff.pow(hop)

    /**
     * Damage of chain hop [hop] (0 is the primary target): 0.6·Q·0.6^hop·M.
     * Each hop takes 0.6 of the previous hop before M, and M applies only to its own target,
     * so a chain of metal-clad mobs does not grow from hop to hop.
     */
    fun chainDamage(q: Double, hop: Int, m: Double, p: ChargeParams): Double = chainBase(q, hop, p) * m

    /**
     * The next link among [candidates] not hit yet: the closest one within its own range,
     * 6 blocks for a conductive candidate (M > 1) and 4 for the rest. Null ends the chain.
     */
    fun nextLink(candidates: List<ChainCandidate>, p: ChargeParams): Int? =
        candidates.withIndex()
            .filter { (_, c) -> c.distance <= chainRange(c.vulnerability, p) }
            .minByOrNull { (_, c) -> c.distance }
            ?.index

    /** r_w = min(8, 3 + L / 5) */
    fun spreadRadius(level: Int, p: ChargeParams): Double =
        min(p.spreadRadiusMax, p.spreadRadiusBase + p.spreadRadiusPerLevel * level)

    /**
     * Water spread before the target's M: 0.4·0.6·Q·(1 − d / r_w). It starts from the base 0.6·Q, not from D,
     * so the M of the primary target doesn't multiply into the water, as in the chain.
     */
    fun spreadBase(q: Double, distance: Double, radius: Double, p: ChargeParams): Double =
        p.spreadFactor * dischargeDamage(q, 1.0, p) * proximity(distance, radius)

    /** Water spread from the primary target: D_water = 0.4·0.6·Q·(1 − d / r_w)·M */
    fun spreadDamage(q: Double, distance: Double, radius: Double, m: Double, p: ChargeParams): Double =
        spreadBase(q, distance, radius, p) * m

    /** Q ≥ 50% Qmax: visual lightning and the horn sound. */
    fun isStrongDischarge(q: Double, qMax: Double, p: ChargeParams): Boolean = q >= p.strongDischarge * qMax

    /** Q ≥ 90% Qmax: the "vaporized" death message. */
    fun isOvercharged(q: Double, qMax: Double, p: ChargeParams): Boolean = q >= p.overcharged * qMax
}
