package com.equi.channelingoverhaul.core

/**
 * Balance numbers used by [ChargeMath]. Defaults are the starting values from DESIGN.md.
 * In game the live values come from the server config (`config.ModConfig.params`).
 *
 * Charge is in charge units, time in seconds, distances in blocks.
 */
data class ChargeParams(
    // Capacity: Qmax(L) = capacityBase + capacityPerLevel·L
    val capacityBase: Double = 10.0,
    val capacityPerLevel: Double = 6.0,

    // Gathering radius: R(L) = min(radiusMax, radiusBase + radiusPerSqrtLevel·√L)
    val radiusBase: Double = 2.0,
    val radiusPerSqrtLevel: Double = 2.0,
    val radiusMax: Double = 16.0,

    // Leak outside charging: Q ← Q·2^(−t / leakHalfLife)
    val leakHalfLife: Double = 150.0,

    // A stuck trident charges every chargeIntervalTicks and rescans its sources every rescanInterval seconds
    val chargeIntervalTicks: Int = 10,
    val rescanInterval: Double = 5.0,

    // Sky inflow S, charge per second
    val skyClear: Double = 0.1,
    val skyRain: Double = 0.5,
    val skySnow: Double = 0.35,
    val skyThunderRain: Double = 1.5,
    val skyThunderSnow: Double = 1.0,

    // Antenna multiplier A
    val antennaMax: Double = 3.0,
    val copperWeight: Double = 0.1,
    val rodWeight: Double = 0.5,
    val rodFalloff: Double = 0.5,
    val antennaWaterWeight: Double = 0.02,
    val antennaWaterMax: Double = 0.3,

    // Lightning strike within the radius, fraction of Qmax
    val strikeRodBonus: Double = 0.5,
    val strikeNearbyBonus: Double = 0.25,

    // Windup: inflow factor and the cap as a fraction of Qmax
    val windupFactor: Double = 0.5,
    val windupCap: Double = 0.3,
    // A winding up player looks for copper, lightning rods and water around them at most this often, seconds
    val windupRescanInterval: Double = 1.0,

    // Discharge: D = damagePerCharge·Q·M
    val damagePerCharge: Double = 0.6,
    val minDischargeCharge: Double = 3.0,
    val vulnerabilityMax: Double = 2.0,
    val wetBonus: Double = 0.25,
    // Fractions of Qmax: visual lightning and horn, "vaporized" death message
    val strongDischarge: Double = 0.5,
    val overcharged: Double = 0.9,
    // Discharge sound: a strong one plays the horn at hornVolumeBase + hornVolumePerCharge·Q/Qmax,
    // a weak one only a quiet crackle
    val hornVolumeBase: Double = 1.0,
    val hornVolumePerCharge: Double = 4.0,
    val crackleVolume: Double = 0.3,
    val cracklePitch: Double = 1.6,
    // With distantThunder, discharges of this Channeling level and up thunder for the whole dimension
    val distantThunderMinLevel: Int = 20,

    // Chain: N(L) = min(chainMaxJumps, ⌊L / chainLevelsPerJump⌋)
    val chainLevelsPerJump: Int = 3,
    val chainMaxJumps: Int = 8,
    val chainRange: Double = 4.0,
    val chainConductiveRange: Double = 6.0,
    val chainFalloff: Double = 0.6,
    val chainHopDelayTicks: Int = 2,

    // Water spread: r_w = min(spreadRadiusMax, spreadRadiusBase + spreadRadiusPerLevel·L)
    val spreadRadiusBase: Double = 3.0,
    val spreadRadiusPerLevel: Double = 0.2,
    val spreadRadiusMax: Double = 8.0,
    val spreadFactor: Double = 0.4,
    // "Same water body only" fills at most this many water blocks from the primary target
    val spreadFloodLimit: Int = 512,
)
