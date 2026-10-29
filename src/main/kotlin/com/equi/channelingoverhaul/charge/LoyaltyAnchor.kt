package com.equi.channelingoverhaul.charge

import com.equi.channelingoverhaul.core.ConductorType
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.mixin.AbstractArrowAccessor
import com.equi.channelingoverhaul.registry.ModDataMaps
import net.minecraft.world.entity.projectile.arrow.ThrownTrident

/**
 * A Loyalty trident with Channeling stuck in copper or a lightning rod (by the `conductivity` data map)
 * stays there until picked up, so it can charge at a station. Anything else returns as in vanilla.
 */
object LoyaltyAnchor {
    @JvmStatic
    fun holds(trident: ThrownTrident): Boolean {
        if (!trident.isStuck) return false
        // The client knows neither the block the trident hit nor its enchantments: it waits for the server to start the return
        if (trident.level().isClientSide) return true
        if (TridentCharge.level(trident.pickupItemStackOrigin, trident.level().registryAccess()) <= 0) return false
        val hit = (trident as AbstractArrowAccessor).`channeling_overhaul$getLastState`() ?: return false
        val type = hit.typeHolder().getData(ModDataMaps.CONDUCTIVITY)?.type
        return type == ConductorType.COPPER || type == ConductorType.LIGHTNING_ROD
    }
}
