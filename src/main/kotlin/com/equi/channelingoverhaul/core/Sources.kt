package com.equi.channelingoverhaul.core

/** What a block adds to the antenna. */
enum class ConductorType {
    COPPER, LIGHTNING_ROD, WATER;

    val serializedName: String = name.lowercase()
}

/**
 * Value of the `conductivity` block data map. [conductivity] is `c` and only matters for copper:
 * lightning rods ignore oxidation, water counts only as a source block.
 */
data class Conductivity(val type: ConductorType = ConductorType.COPPER, val conductivity: Double = 1.0)

/** A copper block near the trident. */
data class Conductor(val distance: Double, val conductivity: Double)

/** Antenna sources around a trident. Distances go from the trident to block centers, in blocks. */
data class SourceScan(
    val copper: List<Conductor> = emptyList(),
    val rods: List<Double> = emptyList(),
    val water: List<Double> = emptyList(),
) {
    class Builder {
        private val copper = mutableListOf<Conductor>()
        private val rods = mutableListOf<Double>()
        private val water = mutableListOf<Double>()

        /** [isFluidSource]: the block holds a source fluid, not a flowing one. */
        fun add(distance: Double, source: Conductivity, isFluidSource: Boolean) {
            when (source.type) {
                ConductorType.COPPER -> copper += Conductor(distance, source.conductivity)
                ConductorType.LIGHTNING_ROD -> rods += distance
                ConductorType.WATER -> if (isFluidSource) water += distance
            }
        }

        fun build() = SourceScan(copper.toList(), rods.toList(), water.toList())
    }

    companion object {
        val EMPTY = SourceScan()
    }
}

/**
 * Antenna multiplier with the share of each kind of source. [water] is already capped,
 * [total] = min(antennaMax, 1 + copper + rods + water).
 */
data class Antenna(val copper: Double, val rods: Double, val water: Double, val total: Double)
