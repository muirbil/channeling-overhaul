package com.equi.channelingoverhaul.config

/** Who hears that a mob was killed by a discharge, on top of what vanilla tells. */
enum class DeathMessageMode {
    /** Only vanilla: the owner of a tamed animal */
    VANILLA,
    /** Everyone, for mobs with a custom name and tamed animals */
    NAMED,
    /** Everyone, for any mob */
    ALL,
}

/** Switches of the server config; the numbers live in [com.equi.channelingoverhaul.core.ChargeParams]. */
data class DischargeRules(
    /** With PvP on, the chain and the water hit players other than the owner */
    val chainHitsPlayers: Boolean = true,
    /** Water spreads only through the water body of the primary target */
    val sameWaterBody: Boolean = false,
    val deathMessages: DeathMessageMode = DeathMessageMode.NAMED,
    /** An overcharged discharge of a high level also thunders far away, for the whole dimension */
    val distantThunder: Boolean = false,
    /** Q, M and D of each discharge, hop and water hit in the owner's chat */
    val debugChat: Boolean = false,
)
