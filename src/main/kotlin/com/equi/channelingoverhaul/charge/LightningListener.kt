package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.core.ConductorType
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.discharge.DischargeEffects
import com.equi.channelingoverhaul.mixin.LightningBoltAccessor
import com.equi.channelingoverhaul.registry.ModDataMaps
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

/**
 * A real lightning bolt within R(L) of a stuck trident with Channeling adds charge at once: +50% Qmax when it
 * strikes a lightning rod, +25% elsewhere, never above Qmax. One bolt charges every trident in reach.
 * Visual-only bolts and our `discharge_bolt` (not a `LightningBolt`) give nothing.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object LightningListener {
    /** Last, so a bolt another listener keeps out of the world charges nothing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onJoin(event: EntityJoinLevelEvent) {
        val bolt = event.entity as? LightningBolt ?: return
        val level = event.level as? ServerLevel ?: return
        if ((bolt as LightningBoltAccessor).`channeling_overhaul$isVisualOnly`()) return
        strike(level, bolt.position(), ModConfig.params)
    }

    private fun strike(level: ServerLevel, at: Vec3, p: ChargeParams) {
        val intoRod = intoRod(level, at)
        val now = level.gameTime
        for (trident in StuckTridentCharger.stuckIn(level)) {
            if (!trident.isStuck) continue
            val stack = trident.pickupItemStackOrigin
            val channeling = TridentCharge.level(stack, level.registryAccess())
            val distance = trident.position().distanceTo(at)
            if (channeling <= 0 || distance > ChargeMath.radius(channeling, p)) continue
            val qMax = ChargeMath.capacity(channeling, p)
            // A stuck trident doesn't leak: its stored charge is current
            val before = TridentCharge.stored(stack)
            val after = ChargeMath.strike(before, qMax, intoRod, p)
            TridentCharge.write(stack, after, now)
            StationSync.updateCharge(trident, after)
            DischargeEffects.burst(level, trident)
            DischargeEffects.debug(
                trident.owner as? ServerPlayer,
                if (intoRod) "chat.channeling_overhaul.lightning.debug.rod" else "chat.channeling_overhaul.lightning.debug",
                distance, after - before, after, qMax,
            )
        }
    }

    /**
     * Natural lightning lands on top of a rod and strikes the block under it, like `LightningBolt.getStrikePosition`;
     * a bolt summoned by Channeling where the trident hit a rod stands in the rod block itself.
     */
    private fun intoRod(level: ServerLevel, at: Vec3): Boolean =
        listOf(BlockPos.containing(at.x, at.y - 1.0e-6, at.z), BlockPos.containing(at)).any {
            level.getBlockState(it).typeHolder().getData(ModDataMaps.CONDUCTIVITY)?.type == ConductorType.LIGHTNING_ROD
        }
}
