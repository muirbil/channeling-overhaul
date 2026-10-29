package com.equi.channelingoverhaul.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the client knows of a stuck trident: enough to draw its gathering and glow, nothing to compute with. */
data class StationView(
    val charge: Double,
    val qMax: Double,
    val radius: Double,
    /** I = S·A */
    val inflow: Double,
    /** A, to split the sparks between the sky and the sources */
    val antenna: Double,
)

/**
 * Numbers of the look. They shape no gameplay, so they live here rather than in the server config;
 * the client config only scales the particles.
 */
data class VisualParams(
    /** Gathering sparks per second for each charge per second of inflow, and their ceiling */
    val sparksPerInflow: Double = 6.0,
    val maxSparksPerSecond: Double = 30.0,
    /** The charge counts as full from this fraction of Qmax: saturating charging only approaches Qmax */
    val fullCharge: Double = 0.995,
    /** The server sends a stuck trident's state again when the charge moved by this fraction of Qmax... */
    val syncCharge: Double = 0.01,
    /** ...or the inflow by this fraction of itself */
    val syncInflow: Double = 0.05,
)

/** Pure functions of the look: glow stages of the trident item, spark rates, when to sync. */
object Visuals {
    /** Charge fractions where the item glow steps up; also the thresholds of `assets/minecraft/items/trident.json`. */
    val GLOW_THRESHOLDS = listOf(0.25, 0.5, 0.75, 1.0)

    fun isFull(charge: Double, qMax: Double, v: VisualParams): Boolean = qMax > 0.0 && charge >= v.fullCharge * qMax

    /** Q / Qmax in 0..1, snapped to 1 once full, so the last glow stage shows on a trident that reached Qmax. */
    fun chargeFraction(charge: Double, qMax: Double, v: VisualParams): Double = when {
        qMax <= 0.0 -> 0.0
        isFull(charge, qMax, v) -> 1.0
        else -> (charge / qMax).coerceIn(0.0, 1.0)
    }

    /** Glow stage 0..4 of a charge [fraction]: how many thresholds it has reached. */
    fun glowStage(fraction: Double): Int = GLOW_THRESHOLDS.count { fraction >= it }

    /** Gathering sparks per second: proportional to the inflow, capped, scaled by the client [intensity]. */
    fun sparksPerSecond(inflow: Double, intensity: Double, v: VisualParams): Double =
        min(v.maxSparksPerSecond, v.sparksPerInflow * max(0.0, inflow)) * intensity

    /** Sparks to start this tick at [perSecond]: the whole part, plus one more with the chance of the rest. [roll] is in 0..1. */
    fun sparksThisTick(perSecond: Double, roll: Double): Int = floor(perSecond / ChargeMath.TICKS_PER_SECOND + roll).toInt()

    /** Whether the client should hear of [new]: first sight, a noticeable change of charge or inflow, or the full mark crossed. */
    fun needsSync(old: StationView?, new: StationView, v: VisualParams): Boolean {
        if (old == null || old.qMax != new.qMax || old.radius != new.radius) return true
        if (isFull(old.charge, old.qMax, v) != isFull(new.charge, new.qMax, v)) return true
        if (abs(new.charge - old.charge) >= v.syncCharge * new.qMax) return true
        if ((old.inflow == 0.0) != (new.inflow == 0.0)) return true
        return abs(new.inflow - old.inflow) >= v.syncInflow * max(old.inflow, new.inflow)
    }
}
