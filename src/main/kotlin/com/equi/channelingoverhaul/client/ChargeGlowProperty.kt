package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.StationSync
import com.equi.channelingoverhaul.core.Visuals
import com.equi.channelingoverhaul.data.TridentCharge
import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent

/**
 * `channeling_overhaul:charge`, Q / Qmax of a trident stack in 0..1 (1 once full), for the `range_dispatch`
 * of `assets/minecraft/items/trident.json`. 0 without Channeling or a level to read the enchantments from.
 */
class ChargeGlowProperty : RangeSelectItemModelProperty {
    override fun get(itemStack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
        level ?: return 0.0f
        val channeling = TridentCharge.level(itemStack, level.registryAccess())
        if (channeling <= 0) return 0.0f
        val charge = TridentCharge.peek(itemStack, level.gameTime)
        return Visuals.chargeFraction(charge, TridentCharge.capacity(channeling), StationSync.VISUALS).toFloat()
    }

    override fun type(): MapCodec<ChargeGlowProperty> = CODEC

    @EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
    object Registration {
        @SubscribeEvent
        fun onRegister(event: RegisterRangeSelectItemModelPropertyEvent) {
            event.register(Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, "charge"), CODEC)
        }
    }

    companion object {
        val CODEC: MapCodec<ChargeGlowProperty> = MapCodec.unit(ChargeGlowProperty())
    }
}
