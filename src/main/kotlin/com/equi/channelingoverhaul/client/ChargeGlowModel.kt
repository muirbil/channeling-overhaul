package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.StationSync
import com.equi.channelingoverhaul.core.Visuals
import com.equi.channelingoverhaul.data.TridentCharge
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper

/**
 * The flat trident model with the glow of its charge. `overrides` can't do this: `ItemRenderer.render` swaps
 * in the flat model for the GUI, the ground and item frames after they were resolved on `trident_in_hand`,
 * so the model itself picks the stage by the stack, in `getRenderPasses`. In hand vanilla draws the 3D model
 * and this one takes no part.
 */
class ChargeGlowModel(base: BakedModel, private val stages: List<BakedModel>) : BakedModelWrapper<BakedModel>(base) {
    /** The wrapped model would return itself here, and `ItemRenderer` would go on without us. */
    override fun applyTransform(context: ItemDisplayContext, poseStack: PoseStack, leftHand: Boolean): BakedModel {
        super.applyTransform(context, poseStack, leftHand)
        return this
    }

    override fun getRenderPasses(stack: ItemStack, fabulous: Boolean): List<BakedModel> {
        val stage = Visuals.glowStage(chargeFraction(stack))
        return if (stage > 0) listOf(stages[stage - 1]) else super.getRenderPasses(stack, fabulous)
    }

    /** Q / Qmax of a trident stack in 0..1 (1 once full); 0 without Channeling or a level to read the enchantments from. */
    private fun chargeFraction(stack: ItemStack): Double {
        val level = Minecraft.getInstance().level ?: return 0.0
        val channeling = TridentCharge.level(stack, level.registryAccess())
        if (channeling <= 0) return 0.0
        val charge = TridentCharge.peek(stack, level.gameTime)
        return Visuals.chargeFraction(charge, TridentCharge.capacity(channeling), StationSync.VISUALS)
    }

    @EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
    object Registration {
        private val TRIDENT = ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("trident"))

        /** `assets/channeling_overhaul/models/item/trident_charge_N.json`, one for each of `Visuals.GLOW_THRESHOLDS`. */
        private val STAGES = Visuals.GLOW_THRESHOLDS.indices.map {
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ChannelingOverhaul.ID, "item/trident_charge_${it + 1}"))
        }

        @SubscribeEvent
        fun onRegisterModels(event: ModelEvent.RegisterAdditional) {
            STAGES.forEach(event::register)
        }

        @SubscribeEvent
        fun onBaked(event: ModelEvent.ModifyBakingResult) {
            val models = event.models
            models[TRIDENT] = ChargeGlowModel(models.getValue(TRIDENT), STAGES.map(models::getValue))
        }
    }
}
