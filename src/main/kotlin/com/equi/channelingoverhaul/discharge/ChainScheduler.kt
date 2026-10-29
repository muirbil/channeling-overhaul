package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChainCandidate
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.registry.ModDamageTypes
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.max

/** The chain of one discharge in progress: entity ids and a position, no entity references. */
class ChainRun(
    val origin: DischargeOrigin,
    /** Q of the discharge */
    val charge: Double,
    val hops: Int,
    /** Everyone this discharge has hit: the primary target, the water and the chain so far */
    val hit: MutableSet<Int>,
    var lastId: Int,
    /** Where the last link was when last seen, the chain goes on from here if it is gone */
    var lastPos: Vec3,
    var nextTick: Int,
) {
    var done = 0
}

/**
 * Chain hops of discharges, one every [ChargeParams.chainHopDelayTicks] on the server tick.
 * Each hop goes to the closest target not hit yet within 4 blocks of the previous link (6 for M > 1),
 * with base 0.6·Q·0.6ⁿ times the target's own M, at most N(L) = min(8, ⌊L / 3⌋) hops.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object ChainScheduler {
    private val runs = ArrayList<ChainRun>()

    fun start(level: ServerLevel, origin: DischargeOrigin, charge: Double, channeling: Int, primary: LivingEntity, hit: MutableSet<Int>, p: ChargeParams) {
        val hops = ChargeMath.chainJumps(channeling, p)
        if (hops <= 0) return
        runs += ChainRun(origin, charge, hops, hit, primary.id, primary.boundingBox.center, level.server.tickCount + p.chainHopDelayTicks)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (runs.isEmpty()) return
        val now = event.server.tickCount
        for (run in runs.toList()) {
            if (now < run.nextTick) continue
            if (hop(event.server, run)) run.nextTick = now + ModConfig.params.chainHopDelayTicks else runs.remove(run)
        }
    }

    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) = runs.clear()

    /** Makes the next hop of [run]; false when the chain is over. */
    private fun hop(server: MinecraftServer, run: ChainRun): Boolean {
        val level = server.getLevel(run.origin.dimension) ?: return false
        val p = ModConfig.params
        val rules = ModConfig.rules
        // A link that died or vanished between hops passes the chain on from where it was last
        val last = level.getEntity(run.lastId)
        val from = if (last != null && last.isAlive) last.boundingBox.center else run.lastPos
        if (!level.isLoaded(BlockPos.containing(from))) return false

        val source = DischargeTargets.source(level, ModDamageTypes.DISCHARGE_CHAIN, run.origin)
        val area = AABB(from, from).inflate(max(p.chainRange, p.chainConductiveRange))
        val candidates = level.getEntitiesOfClass(LivingEntity::class.java, area) {
            it.id !in run.hit && DischargeTargets.canHit(level, it, run.origin, source, rules)
        }
        val m = candidates.map { DischargeTargets.vulnerability(it, p) }
        val index = ChargeMath.nextLink(candidates.mapIndexed { i, c -> ChainCandidate(c.boundingBox.center.distanceTo(from), m[i]) }, p)
            ?: return false

        val target = candidates[index]
        val hop = run.done + 1
        val base = ChargeMath.chainBase(run.charge, hop, p)
        val damage = base * m[index]
        val to = target.boundingBox.center
        run.hit += target.id
        target.hurtServer(level, source, damage.toFloat())
        DischargeEffects.arc(level, from, to)
        DischargeEffects.crackle(level, target, p)
        DischargeEffects.debug(level, run.origin, "chat.channeling_overhaul.chain.debug", hop, target.displayName, base, m[index], damage)

        run.done = hop
        run.lastId = target.id
        run.lastPos = to
        return hop < run.hops
    }
}
