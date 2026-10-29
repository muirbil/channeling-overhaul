package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.config.DischargeRules
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.registry.ModDamageTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

/**
 * Spread of a discharge through water: a primary target standing in water hits every living entity in water
 * within r_w = min(8, 3 + L / 5) with D_water = 0.4·0.6·Q·(1 − d / r_w)·M, right away. Only from the primary target;
 * chain links don't spread.
 */
object WaterSpread {
    fun spread(
        level: ServerLevel, primary: LivingEntity, charge: Double, channeling: Int,
        origin: DischargeOrigin, hit: MutableSet<Int>, p: ChargeParams, rules: DischargeRules,
    ) {
        if (!primary.isInWater) return
        val radius = ChargeMath.spreadRadius(channeling, p)
        val center = primary.boundingBox.center
        val source = DischargeTargets.source(level, ModDamageTypes.DISCHARGE_WATER, origin)
        val floodLimit = if (rules.sameWaterBody) p.spreadFloodLimit else 0
        val targets = targets(level, primary, radius, floodLimit) {
            it.id !in hit && DischargeTargets.canHit(level, it, origin, source, rules)
        }
        for (target in targets) {
            val distance = target.boundingBox.center.distanceTo(center)
            val base = ChargeMath.spreadBase(charge, distance, radius, p)
            val m = DischargeTargets.vulnerability(target, p)
            hit += target.id
            target.hurtServer(level, source, (base * m).toFloat())
            DischargeEffects.waterHit(level, target)
            DischargeEffects.debug(level, origin, "chat.channeling_overhaul.water.debug", target.displayName, distance, base, m, base * m)
        }
    }

    /**
     * Living entities in water strictly within [radius] of [primary], nearest first. With [floodLimit] above 0,
     * only those touching the water body of [primary], filled up to that many blocks.
     */
    fun targets(level: ServerLevel, primary: LivingEntity, radius: Double, floodLimit: Int, filter: (LivingEntity) -> Boolean): List<LivingEntity> {
        val center = primary.boundingBox.center
        val body = if (floodLimit > 0) waterBody(level, primary, radius, floodLimit) else null
        return level.getEntitiesOfClass(LivingEntity::class.java, AABB(center, center).inflate(radius)) {
            it !== primary && it.isInWater && it.boundingBox.center.distanceTo(center) < radius &&
                (body == null || blocks(it).any(body::contains)) && filter(it)
        }.sortedBy { it.boundingBox.center.distanceTo(center) }
    }

    /** Water blocks connected to the ones [primary] stands in, within reach of the radius, at most [limit]. */
    private fun waterBody(level: ServerLevel, primary: LivingEntity, radius: Double, limit: Int): Set<BlockPos> {
        val center = primary.boundingBox.center
        val body = LinkedHashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        for (pos in blocks(primary)) if (isWater(level, pos) && body.add(pos)) queue.add(pos)
        while (queue.isNotEmpty() && body.size < limit) {
            val pos = queue.removeFirst()
            for (direction in Direction.entries) {
                val next = pos.relative(direction)
                if (body.size >= limit) break
                if (next in body || !isWater(level, next) || next.center.distanceTo(center) > radius + 1.0) continue
                body.add(next)
                queue.add(next)
            }
        }
        return body
    }

    private fun isWater(level: ServerLevel, pos: BlockPos) = level.isLoaded(pos) && level.getFluidState(pos).`is`(FluidTags.WATER)

    /** Blocks the bounding box of [entity] is in. */
    private fun blocks(entity: Entity): List<BlockPos> {
        val box = entity.boundingBox.deflate(1.0e-4)
        return BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ),
        ).map(BlockPos::immutable)
    }
}
