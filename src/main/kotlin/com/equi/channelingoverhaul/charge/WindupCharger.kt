package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.SourceScan
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.discharge.DischargeEffects
import com.equi.channelingoverhaul.registry.ModAttachments
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import kotlin.math.roundToLong

/** One windup of a player, kept off the stack until the release. */
class WindupState {
    var active = false
    /** Charge when the windup started and as it is now */
    var start = 0.0
    var charge = 0.0
    var ticks = 0
    var scan: SourceScan = SourceScan.EMPTY
    var nextScan = Long.MIN_VALUE
}

/**
 * Recharge while a player holds right click with a trident: S·A at the player times 0.5, never above 30% Qmax.
 * The charge is written to the stack only on release, right before `TridentItem.releaseUsing` copies it into the
 * thrown trident: a component change on the held stack each tick would sync it to the client, and NeoForge plays
 * the re-equip animation for any new stack. A windup cut short by switching slots (no release) is lost.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object WindupCharger {
    @SubscribeEvent
    fun onStart(event: LivingEntityUseItemEvent.Start) {
        val player = event.entity as? ServerPlayer ?: return
        val state = player.getData(ModAttachments.WINDUP)
        state.active = TridentCharge.level(event.item, player.level().registryAccess()) > 0
        if (!state.active) return
        // The leak of the inventory ends here, a windup is charging. Peeked, not read: the stack stays as it is till the release
        state.start = TridentCharge.peek(event.item, player.level().gameTime)
        state.charge = state.start
        state.ticks = 0
        state.nextScan = Long.MIN_VALUE
    }

    /** One tick of use: one twentieth of a second of windup, however the game time goes. */
    @SubscribeEvent
    fun onTick(event: LivingEntityUseItemEvent.Tick) {
        val player = event.entity as? ServerPlayer ?: return
        val level = player.level()
        val state = player.getData(ModAttachments.WINDUP)
        if (!state.active) return
        val channeling = TridentCharge.level(event.item, level.registryAccess())
        if (channeling <= 0) return
        val p = ModConfig.params
        val now = level.gameTime
        if (now >= state.nextScan) {
            state.scan = SourceScanner.scan(level, player.position(), ChargeMath.radius(channeling, p))
            state.nextScan = now + (p.windupRescanInterval * ChargeMath.TICKS_PER_SECOND).roundToLong()
        }
        val inflow = StationReading.at(level, player.blockPosition(), channeling, state.scan, p).inflow
        val before = state.charge
        state.charge = ChargeMath.windup(state.charge, inflow, ChargeMath.ticksToSeconds(1), ChargeMath.capacity(channeling, p), p)
        state.ticks++
        sparks(player, state.charge > before)
    }

    /** Sparks at the trident while the windup really adds charge; synced only when that changes. */
    private fun sparks(player: ServerPlayer, adding: Boolean) {
        if ((player.getExistingDataOrNull(ModAttachments.WINDUP_SPARKS) ?: false) != adding) player.setData(ModAttachments.WINDUP_SPARKS, adding)
    }

    /** Release, thrown or not: the windup goes into the stack before vanilla copies it. */
    @SubscribeEvent
    fun onStop(event: LivingEntityUseItemEvent.Stop) {
        val player = event.entity as? ServerPlayer ?: return
        val state = player.getData(ModAttachments.WINDUP)
        if (!state.active) return
        state.active = false
        sparks(player, false)
        TridentCharge.write(event.item, state.charge, player.level().gameTime)
        val channeling = TridentCharge.level(event.item, player.level().registryAccess())
        DischargeEffects.debug(
            player, "chat.channeling_overhaul.windup.debug",
            state.charge - state.start, ChargeMath.ticksToSeconds(state.ticks.toLong()), state.charge,
            ChargeMath.capacity(channeling, ModConfig.params),
        )
    }
}
