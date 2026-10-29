package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModClientConfig
import com.equi.channelingoverhaul.network.SparksPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws the sparks the server asks for ([SparksPayload]): zigzag chain arcs, hits through water, bursts. */
@EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
object ClientSparks {
    /** An arc lives this many ticks, with a fresh zigzag each tick */
    private const val ARC_TICKS = 4
    /** Length of one straight piece of the zigzag and the most its joints stray, blocks */
    private const val ARC_PIECE = 0.6
    private const val ARC_BEND = 0.35
    /** Spacing of the sparks along the zigzag at full intensity, blocks */
    private const val ARC_STEP = 0.2
    private const val BURST_SPARKS = 12
    private const val WATER_BUBBLES = 10

    private class Arc(val from: Vec3, val to: Vec3, var ticks: Int)

    private val arcs = ArrayList<Arc>()

    fun play(payload: SparksPayload) {
        val level = Minecraft.getInstance().level ?: return
        when (payload.kind) {
            SparksPayload.Kind.ARC -> arcs += Arc(payload.from, payload.to, ARC_TICKS)
            SparksPayload.Kind.BURST -> burst(level, payload.from, payload.to, ParticleTypes.ELECTRIC_SPARK, BURST_SPARKS)
            SparksPayload.Kind.WATER -> {
                burst(level, payload.from, payload.to, ParticleTypes.ELECTRIC_SPARK, BURST_SPARKS)
                burst(level, payload.from, payload.to, ParticleTypes.BUBBLE, WATER_BUBBLES)
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level == null) {
            arcs.clear()
            return
        }
        if (minecraft.isPaused) return
        val iterator = arcs.iterator()
        while (iterator.hasNext()) {
            val arc = iterator.next()
            zigzag(level, arc.from, arc.to)
            if (--arc.ticks <= 0) iterator.remove()
        }
    }

    /** Sparks along a broken line from [from] to [to] with random joints, sparser at a lower intensity. */
    fun zigzag(level: ClientLevel, from: Vec3, to: Vec3) {
        val step = min(1.0, ARC_STEP / max(0.05, ModClientConfig.intensity))
        val pieces = max(2, ceil(from.distanceTo(to) / ARC_PIECE).toInt())
        var start = from
        for (i in 1..pieces) {
            val end = if (i == pieces) to else from.lerp(to, i.toDouble() / pieces).add(bend(level), bend(level), bend(level))
            val steps = max(1, ceil(start.distanceTo(end) / step).toInt())
            for (s in 0 until steps) {
                val point = start.lerp(end, s.toDouble() / steps)
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 0.0, 0.0, 0.0)
            }
            start = end
        }
    }

    /** [count] particles at full intensity, anywhere in the box of [center] and [half] size, drifting a little. */
    fun burst(level: ClientLevel, center: Vec3, half: Vec3, particle: ParticleOptions, count: Int) {
        val random = level.random
        repeat(max(1, (count * ModClientConfig.intensity).roundToInt())) {
            level.addParticle(
                particle,
                center.x + (random.nextDouble() * 2 - 1) * half.x,
                center.y + (random.nextDouble() * 2 - 1) * half.y,
                center.z + (random.nextDouble() * 2 - 1) * half.z,
                (random.nextDouble() - 0.5) * 0.1, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.1,
            )
        }
    }

    private fun bend(level: ClientLevel) = (level.random.nextDouble() * 2 - 1) * ARC_BEND
}
