package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.core.Antenna
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.core.Precipitation
import com.equi.channelingoverhaul.core.SourceScan
import com.equi.channelingoverhaul.core.Weather
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome

/** Everything that sets the inflow of a stuck trident, kept for the debug command and game tests. */
data class StationReading(
    val channeling: Int,
    val radius: Double,
    val canSeeSky: Boolean,
    val precipitation: Precipitation,
    val weather: Weather,
    val sky: Double,
    val scan: SourceScan,
    val antenna: Antenna,
) {
    /** I = S·A */
    val inflow: Double get() = ChargeMath.inflow(sky, antenna.total)

    companion object {
        /** Reads sky and weather at the trident now, with sources from [scan]. */
        fun of(trident: ThrownTrident, channeling: Int, scan: SourceScan, p: ChargeParams): StationReading =
            at(trident.level(), trident.blockPosition(), channeling, scan, p)

        /** Reads sky and weather at [pos] now, with sources from [scan]: a stuck trident or a winding up player. */
        fun at(level: Level, pos: BlockPos, channeling: Int, scan: SourceScan, p: ChargeParams): StationReading {
            val canSeeSky = level.canSeeSky(pos)
            val precipitation = when (level.getBiome(pos).value().getPrecipitationAt(pos, level.seaLevel)) {
                Biome.Precipitation.RAIN -> Precipitation.RAIN
                Biome.Precipitation.SNOW -> Precipitation.SNOW
                else -> Precipitation.NONE
            }
            val weather = weather(level)
            val radius = ChargeMath.radius(channeling, p)
            return StationReading(
                channeling,
                radius,
                canSeeSky,
                precipitation,
                weather,
                ChargeMath.skyInflow(canSeeSky, weather, precipitation, p),
                scan,
                ChargeMath.antenna(scan, radius, p),
            )
        }

        /** Sources within R(L) of the trident. */
        fun scan(trident: ThrownTrident, channeling: Int, p: ChargeParams): SourceScan =
            SourceScanner.scan(trident.level(), trident.position(), ChargeMath.radius(channeling, p))

        private fun weather(level: Level) = when {
            level.isThundering -> Weather.THUNDER
            level.isRaining -> Weather.RAIN
            else -> Weather.CLEAR
        }
    }
}
