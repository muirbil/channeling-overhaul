package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.SourceScanner
import com.equi.channelingoverhaul.charge.StationSync
import com.equi.channelingoverhaul.charge.isStuck
import com.equi.channelingoverhaul.config.ModClientConfig
import com.equi.channelingoverhaul.core.StationView
import com.equi.channelingoverhaul.core.Visuals
import com.equi.channelingoverhaul.registry.ModAttachments
import com.equi.channelingoverhaul.registry.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.TridentItem
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * The client look of charging, from the synced [StationView] of stuck tridents within [RANGE] blocks:
 * sparks flying in from the sky and from copper and lightning rods, as many as the inflow, a chime and a flash
 * when a trident fills up, and sparks at a trident in hand while the windup adds charge.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
object ClientStationEffects {
    private const val RANGE = 32.0
    /** Sources around a trident are looked up again after this many ticks */
    private const val SOURCES_TTL = 100L
    /** A gathering spark reaches the trident in about this many ticks */
    private const val MOTE_TICKS = 10.0
    private const val MAX_MOTES = 400
    private const val FULL_SPARKS = 24
    /** Windup sparks per second at full intensity */
    private const val WINDUP_SPARKS = 30.0

    /** A gathering spark on its way: each tick it moves and leaves an `electric_spark`, which lives only 2–3 ticks. */
    private class Mote(var pos: Vec3, val target: Vec3, val step: Double)

    private class Sources(val scannedAt: Long, val positions: List<Vec3>)

    private val motes = ArrayList<Mote>()
    private val sources = HashMap<Int, Sources>()
    /** Whether each trident in sight was full last tick; a trident seen for the first time chimes not */
    private val full = HashMap<Int, Boolean>()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = minecraft.player
        if (level == null || player == null) {
            motes.clear()
            sources.clear()
            full.clear()
            return
        }
        if (minecraft.isPaused) return
        val intensity = ModClientConfig.intensity
        val seen = HashSet<Int>()
        for (trident in level.getEntitiesOfClass(ThrownTrident::class.java, player.boundingBox.inflate(RANGE))) {
            val view = trident.getExistingDataOrNull(ModAttachments.STATION) ?: continue
            if (!trident.isStuck) continue
            seen += trident.id
            val isFull = Visuals.isFull(view.charge, view.qMax, StationSync.VISUALS)
            if (full.put(trident.id, isFull) == false && isFull) fullCharge(level, trident)
            val count = Visuals.sparksThisTick(Visuals.sparksPerSecond(view.inflow, intensity, StationSync.VISUALS), level.random.nextDouble())
            repeat(count) { if (motes.size < MAX_MOTES) motes += mote(level, trident, view) }
        }
        full.keys.retainAll(seen)
        sources.keys.retainAll(seen)
        advanceMotes(level)
        for (other in level.players()) {
            if (other.distanceToSqr(player) <= RANGE * RANGE) windupSparks(level, other, intensity)
        }
    }

    /** From a copper block or a lightning rod with the share of the antenna in the inflow, from the sky otherwise. */
    private fun mote(level: ClientLevel, trident: ThrownTrident, view: StationView): Mote {
        val random = level.random
        val target = trident.position()
        val positions = sourcesOf(level, trident, view)
        val fromSource = positions.isNotEmpty() && random.nextDouble() < (view.antenna - 1.0) / view.antenna
        val start = if (fromSource) {
            positions[random.nextInt(positions.size)].add((random.nextDouble() - 0.5) * 0.8, 0.5, (random.nextDouble() - 0.5) * 0.8)
        } else {
            target.add((random.nextDouble() - 0.5) * 3.0, 3.0 + random.nextDouble() * 2.0, (random.nextDouble() - 0.5) * 3.0)
        }
        return Mote(start, target, start.distanceTo(target) / MOTE_TICKS)
    }

    private fun sourcesOf(level: ClientLevel, trident: ThrownTrident, view: StationView): List<Vec3> {
        val cached = sources[trident.id]
        if (cached != null && level.gameTime - cached.scannedAt < SOURCES_TTL) return cached.positions
        val positions = SourceScanner.conductorPositions(level, trident.position(), view.radius)
        sources[trident.id] = Sources(level.gameTime, positions)
        return positions
    }

    private fun advanceMotes(level: ClientLevel) {
        val iterator = motes.iterator()
        while (iterator.hasNext()) {
            val mote = iterator.next()
            val toTarget = mote.target.subtract(mote.pos)
            if (toTarget.length() <= mote.step) {
                iterator.remove()
                continue
            }
            mote.pos = mote.pos.add(toTarget.normalize().scale(mote.step))
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, mote.pos.x, mote.pos.y, mote.pos.z, 0.0, 0.0, 0.0)
        }
    }

    /** Once, when a trident reaches Qmax: a chime and a flash of sparks. */
    private fun fullCharge(level: ClientLevel, trident: ThrownTrident) {
        level.playLocalSound(trident.x, trident.y, trident.z, ModSounds.FULL_CHARGE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f, false)
        ClientSparks.burst(level, trident.position(), Vec3(0.4, 0.4, 0.4), ParticleTypes.ELECTRIC_SPARK, FULL_SPARKS)
    }

    /** A few sparks at the trident in hand while the server says the windup adds charge. */
    private fun windupSparks(level: ClientLevel, player: Player, intensity: Double) {
        if (!player.isUsingItem || player.useItem.item !is TridentItem) return
        if (player.getExistingDataOrNull(ModAttachments.WINDUP_SPARKS) != true) return
        val random = level.random
        val look = player.lookAngle
        // Roughly where the raised trident is: ahead of the eyes, to the side of the hand
        val side = look.cross(Vec3(0.0, 1.0, 0.0)).normalize().scale(if (player.mainArm == HumanoidArm.RIGHT) 0.4 else -0.4)
        val at = player.eyePosition.add(look.scale(0.5)).add(side).add(0.0, 0.2, 0.0)
        repeat(Visuals.sparksThisTick(WINDUP_SPARKS * intensity, random.nextDouble())) {
            level.addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                at.x + (random.nextDouble() - 0.5) * 0.4, at.y + (random.nextDouble() - 0.5) * 0.4, at.z + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.0, 0.0,
            )
        }
    }
}
