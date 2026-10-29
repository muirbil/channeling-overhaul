package com.equi.channelingoverhaul.discharge

import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * Damage of a discharge, its chain or its water: the thrown trident is the direct entity, its owner
 * the attacker, so the kill counts for the owner. It picks the death message itself. A late chain hop
 * may find the trident picked up and the owner gone, then both are null.
 */
class DischargeDamageSource(
    type: Holder<DamageType>,
    trident: Entity?,
    owner: Entity?,
    /** Q ≥ 90% Qmax */
    val overcharged: Boolean,
    /** The victim wears conductive armor */
    val conductor: Boolean,
    /** Custom name of the thrown trident, null when it has none */
    val tridentName: Component?,
) : DamageSource(type, trident, owner) {
    /**
     * Vanilla puts the killer message in the base key and takes `.item` from the attacker's main hand,
     * while `.player` is only for damage with neither an attacker nor a direct entity. A discharge
     * always has the trident as its direct entity, so it chooses the keys here: `.player` with the killer,
     * `.item` with the thrown trident's name, and the base key, through vanilla, without a killer.
     * Priority: overcharged, then conductor, then the named or plain message.
     */
    override fun getLocalizedDeathMessage(victim: LivingEntity): Component {
        val killer = entity ?: return super.getLocalizedDeathMessage(victim)
        val base = "death.attack.$msgId"
        val key = when {
            overcharged -> "$base.overcharged"
            conductor -> "$base.conductor"
            tridentName != null -> "$base.item"
            else -> "$base.player"
        }
        return Component.translatable(key, victim.displayName, killer.displayName, tridentName ?: Component.empty())
    }
}
