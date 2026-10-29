package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.data.ChargeData
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModDataComponents {
    val REGISTRY: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ChannelingOverhaul.ID)

    /** Saved with the stack and synced to clients for the tooltip and, later, the item model. */
    val CHARGE: DataComponentType<ChargeData> by REGISTRY.registerComponentType("charge") { builder ->
        builder.persistent(ChargeData.CODEC).networkSynchronized(ChargeData.STREAM_CODEC)
    }
}
