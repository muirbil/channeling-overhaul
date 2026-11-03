package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.advancement.ChargeTrigger
import com.equi.channelingoverhaul.advancement.DischargeTrigger
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Advancement triggers of the mod; the advancements and their thresholds are data. */
object ModTriggers {
    val REGISTRY: DeferredRegister<CriterionTrigger<*>> = DeferredRegister.create(Registries.TRIGGER_TYPE, ChannelingOverhaul.ID)

    val CHARGE: DeferredHolder<CriterionTrigger<*>, ChargeTrigger> = REGISTRY.register("charge", Supplier { ChargeTrigger() })
    val DISCHARGE: DeferredHolder<CriterionTrigger<*>, DischargeTrigger> = REGISTRY.register("discharge", Supplier { DischargeTrigger() })
}
