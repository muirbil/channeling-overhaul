package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.core.ConductorType
import com.equi.channelingoverhaul.core.Conductivity
import com.equi.channelingoverhaul.core.SourceScan
import com.equi.channelingoverhaul.registry.ModDataMaps
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.sqrt

/** Finds copper, lightning rods and water around a point through the `conductivity` data map. */
object SourceScanner {
    /** All sources whose block center is within [radius] of [center]. Never loads chunks. */
    fun scan(level: Level, center: Vec3, radius: Double): SourceScan {
        val sources = SourceScan.Builder()
        forEachSource(level, center, radius) { _, distance, source, isFluidSource -> sources.add(distance, source, isFluidSource) }
        return sources.build()
    }

    /** Centers of the copper and lightning rods within [radius]: where the client's gathering sparks start. */
    fun conductorPositions(level: Level, center: Vec3, radius: Double): List<Vec3> {
        val positions = ArrayList<Vec3>()
        forEachSource(level, center, radius) { pos, _, source, _ ->
            if (source.type != ConductorType.WATER) positions += Vec3.atCenterOf(pos)
        }
        return positions
    }

    private fun forEachSource(
        level: Level, center: Vec3, radius: Double, action: (BlockPos, Double, Conductivity, Boolean) -> Unit,
    ) {
        val origin = BlockPos.containing(center)
        val reach = ceil(radius).toInt()
        val radiusSqr = radius * radius
        val pos = BlockPos.MutableBlockPos()
        for (dx in -reach..reach) for (dy in -reach..reach) for (dz in -reach..reach) {
            pos.set(origin.x + dx, origin.y + dy, origin.z + dz)
            val distanceSqr = center.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            if (distanceSqr > radiusSqr || !level.isLoaded(pos)) continue
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            val source = state.typeHolder().getData(ModDataMaps.CONDUCTIVITY) ?: continue
            action(pos, sqrt(distanceSqr), source, state.fluidState.isSource)
        }
    }
}
