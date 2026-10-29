package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.discharge.DischargeBolt
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModEntities {
    val REGISTRY: DeferredRegister.Entities = DeferredRegister.createEntities(ChannelingOverhaul.ID)

    /** Sized, tracked and updated like a vanilla `LightningBolt`. */
    val DISCHARGE_BOLT: EntityType<DischargeBolt> by REGISTRY.registerEntityType("discharge_bolt", ::DischargeBolt, MobCategory.MISC) {
        it.noLootTable().noSave().fireImmune().sized(0.0f, 0.0f).clientTrackingRange(16).updateInterval(Int.MAX_VALUE)
    }
}
