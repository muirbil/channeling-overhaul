package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.core.StationView
import com.equi.channelingoverhaul.core.VisualParams
import com.equi.channelingoverhaul.core.Visuals
import com.equi.channelingoverhaul.registry.ModAttachments
import net.minecraft.world.entity.projectile.arrow.ThrownTrident

/** Keeps the synced [StationView] of a stuck trident, sending it again only on a noticeable change. */
object StationSync {
    val VISUALS = VisualParams()

    fun view(reading: StationReading, charge: Double, qMax: Double) =
        StationView(charge, qMax, reading.radius, reading.inflow, reading.antenna.total)

    fun update(trident: ThrownTrident, view: StationView) {
        if (Visuals.needsSync(trident.getExistingDataOrNull(ModAttachments.STATION), view, VISUALS)) {
            trident.setData(ModAttachments.STATION, view)
        }
    }

    /** The charge alone changed, as from a lightning strike: the rest stays as last sent. */
    fun updateCharge(trident: ThrownTrident, charge: Double) {
        trident.getExistingDataOrNull(ModAttachments.STATION)?.let { update(trident, it.copy(charge = charge)) }
    }

    fun clear(trident: ThrownTrident) {
        if (trident.hasData(ModAttachments.STATION)) trident.removeData(ModAttachments.STATION)
    }
}
