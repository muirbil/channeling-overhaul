package com.equi.channelingoverhaul.advancement

/** Which part of a discharge a hit came from. */
enum class HitKind { PRIMARY, WATER, CHAIN }

/**
 * What one discharge did, for `channeling_overhaul:discharge`: gathered over its hits on the primary target,
 * through the water and along the chain, then checked once the discharge is over.
 */
class DischargeStats {
    /** Different living entities hit; every entity takes one hit at most */
    var targetsHit = 0
        private set
    var kills = 0
        private set
    var waterKills = 0
        private set
    var hops = 0
        private set
    /** The largest computed D on one target, before armor and crits */
    var maxDamage = 0.0
        private set

    /** A hit of computed [damage]; [killed] when the target was alive before it and dead after it. */
    fun hit(kind: HitKind, damage: Double, killed: Boolean) {
        targetsHit++
        if (kind == HitKind.CHAIN) hops++
        if (killed) {
            kills++
            if (kind == HitKind.WATER) waterKills++
        }
        maxDamage = maxOf(maxDamage, damage)
    }
}
