package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.data.TridentCharge
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident

/**
 * Vanilla despawns a stuck trident after 1200 ticks unless it has Loyalty and can be picked up.
 * One with Channeling that can be picked up stays too, so it can charge at a station for more than a minute.
 */
object DespawnGuard {
    @JvmStatic
    fun keeps(trident: ThrownTrident): Boolean =
        trident.pickup == AbstractArrow.Pickup.ALLOWED &&
            TridentCharge.level(trident.pickupItemStackOrigin, trident.level().registryAccess()) > 0
}
