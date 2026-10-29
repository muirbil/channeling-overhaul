package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageType

/**
 * Damage types of the discharge, defined as data in `data/channeling_overhaul/damage_type`.
 * All three are in the tags `is_lightning`, `bypasses_cooldown` (the discharge lands right after the
 * trident's own hit, inside its hurt cooldown) and `no_knockback` (the trident already knocked back).
 */
object ModDamageTypes {
    /** The primary target */
    val DISCHARGE: ResourceKey<DamageType> = key("discharge")
    /** Chain hops, stage 6 */
    val DISCHARGE_CHAIN: ResourceKey<DamageType> = key("discharge_chain")
    /** Spread through water, stage 6 */
    val DISCHARGE_WATER: ResourceKey<DamageType> = key("discharge_water")

    fun holder(level: ServerLevel, key: ResourceKey<DamageType>): Holder<DamageType> =
        level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key)

    private fun key(path: String): ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, path))
}
