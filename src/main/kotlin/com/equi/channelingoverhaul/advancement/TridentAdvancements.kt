package com.equi.channelingoverhaul.advancement

import com.equi.channelingoverhaul.charge.StationSync
import com.equi.channelingoverhaul.core.Visuals
import com.equi.channelingoverhaul.discharge.DischargeOrigin
import com.equi.channelingoverhaul.registry.ModTriggers
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.ThrownTrident

/**
 * Fires the advancement triggers for the owner of a trident, only while that owner is a player online.
 * Debug commands write the charge directly and never come here.
 */
object TridentAdvancements {
    /** A stuck trident went from [before] to [after] charge out of [qMax]. */
    fun charged(trident: ThrownTrident, before: Double, after: Double, qMax: Double, source: ChargeSource) {
        val player = online(trident.owner) ?: return
        val full = !Visuals.isFull(before, qMax, StationSync.VISUALS) && Visuals.isFull(after, qMax, StationSync.VISUALS)
        ModTriggers.CHARGE.get().trigger(player, full, source)
    }

    /** A discharge of the trident of [origin] is over. */
    fun discharged(level: ServerLevel, origin: DischargeOrigin, stats: DischargeStats) {
        val player = online(origin.owner(level)) ?: return
        ModTriggers.DISCHARGE.get().trigger(player, stats)
    }

    /** A player who logged out has left the world, so one found there is online unless just disconnecting. */
    private fun online(owner: Entity?): ServerPlayer? = (owner as? ServerPlayer)?.takeUnless { it.hasDisconnected() }
}
