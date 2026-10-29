package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.core.SourceScan
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.mixin.AbstractArrowAccessor
import com.equi.channelingoverhaul.registry.ModAttachments
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import kotlin.math.roundToLong

/**
 * The trident is stuck in a block (`AbstractArrow.isInGround`, protected in vanilla). A Loyalty
 * trident keeps `isInGround` while it flies back, but that is flight (`isNoPhysics`).
 */
val ThrownTrident.isStuck: Boolean
    get() = (this as AbstractArrowAccessor).`channeling_overhaul$isInGround`() && !isNoPhysics

/** Per-trident bookkeeping of [StuckTridentCharger], kept in an unsaved attachment. */
class ChargerState {
    /** Game time of the last tick this trident ticked at, −1 before the first one. */
    var lastSeen = -1L
    var loadedFromDisk = false
    var stuck = false
    var lastStep = 0L
    var nextScan = 0L
    var scan: SourceScan = SourceScan.EMPTY
}

/**
 * Charges a trident stuck in a block, server side. The charge goes into the stack the trident
 * gives back on pickup. While stuck nothing leaks: every step moves the timestamp, even with no inflow.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object StuckTridentCharger {
    /** Entity ids of the stuck tridents with Channeling, by dimension: what a lightning strike looks through. */
    private val stuck = HashMap<ResourceKey<Level>, MutableSet<Int>>()

    /** The stuck tridents with Channeling in [level], held Loyalty tridents included. */
    fun stuckIn(level: ServerLevel): List<ThrownTrident> =
        stuck[level.dimension()].orEmpty().mapNotNull { level.getEntity(it) as? ThrownTrident }

    @SubscribeEvent
    fun onLeave(event: EntityLeaveLevelEvent) {
        if (event.entity is ThrownTrident && !event.level.isClientSide) stuck[event.level.dimension()]?.remove(event.entity.id)
    }

    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) = stuck.clear()

    @SubscribeEvent
    fun onJoin(event: EntityJoinLevelEvent) {
        if (event.level.isClientSide || !event.loadedFromDisk()) return
        val trident = event.entity as? ThrownTrident ?: return
        trident.getData(ModAttachments.CHARGER).loadedFromDisk = true
    }

    @SubscribeEvent
    fun onTick(event: EntityTickEvent.Post) {
        val trident = event.entity as? ThrownTrident ?: return
        val level = trident.level() as? ServerLevel ?: return
        val state = trident.getData(ModAttachments.CHARGER)
        val now = level.gameTime
        // Missed ticks mean the chunk was unloaded or not ticking: that time neither charges nor leaks
        val resumed = if (state.lastSeen < 0) state.loadedFromDisk else now - state.lastSeen > 1
        state.lastSeen = now

        if (!trident.isStuck) {
            if (state.stuck) {
                stuck[level.dimension()]?.remove(trident.id)
                StationSync.clear(trident)
            }
            state.stuck = false
            return
        }
        val stack = trident.pickupItemStackOrigin
        val channeling = TridentCharge.level(stack, level.registryAccess())
        if (channeling <= 0) return
        val p = ModConfig.params

        if (!state.stuck || resumed) {
            // Just stuck: the leak of the flight and the inventory ends here
            if (resumed) TridentCharge.stamp(stack, now) else TridentCharge.read(stack, now)
            state.stuck = true
            stuck.getOrPut(level.dimension(), ::HashSet) += trident.id
            state.lastStep = now
            rescan(trident, channeling, state, now, p)
            val reading = StationReading.of(trident, channeling, state.scan, p)
            StationSync.update(trident, StationSync.view(reading, TridentCharge.stored(stack), ChargeMath.capacity(channeling, p)))
            return
        }
        if (now - state.lastStep < p.chargeIntervalTicks) return

        if (now >= state.nextScan) rescan(trident, channeling, state, now, p)
        val reading = StationReading.of(trident, channeling, state.scan, p)
        val seconds = ChargeMath.ticksToSeconds(now - state.lastStep)
        val qMax = ChargeMath.capacity(channeling, p)
        val charge = ChargeMath.charge(TridentCharge.stored(stack), reading.inflow, seconds, qMax)
        TridentCharge.write(stack, charge, now)
        StationSync.update(trident, StationSync.view(reading, charge, qMax))
        state.lastStep = now
    }

    /** Scans the sources now and schedules the next scan. */
    fun rescan(trident: ThrownTrident, channeling: Int, state: ChargerState, now: Long, p: ChargeParams) {
        state.scan = StationReading.scan(trident, channeling, p)
        state.nextScan = now + (p.rescanInterval * ChargeMath.TICKS_PER_SECOND).roundToLong()
    }
}
