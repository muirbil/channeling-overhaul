package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.discharge.DischargeBolt
import com.equi.channelingoverhaul.registry.ModEntities
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LightningBoltRenderer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/**
 * Draws a [DischargeBolt] as a vanilla lightning bolt. The vanilla renderer takes a `LightningBolt` but builds
 * the zigzag from its seed alone, so a bolt that never enters the world carries ours, and we follow vanilla
 * if the geometry changes.
 */
class DischargeBoltRenderer(context: EntityRendererProvider.Context) : EntityRenderer<DischargeBolt>(context) {
    private val zigzag = LightningBoltRenderer(context)
    private var carrier: LightningBolt? = null

    override fun render(entity: DischargeBolt, yaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, light: Int) {
        val bolt = carrier?.takeIf { it.level() === entity.level() }
            ?: EntityType.LIGHTNING_BOLT.create(entity.level())?.also { carrier = it }
            ?: return
        bolt.seed = entity.seed
        zigzag.render(bolt, yaw, partialTicks, poseStack, buffer, light)
    }

    override fun getTextureLocation(entity: DischargeBolt): ResourceLocation = TextureAtlas.LOCATION_BLOCKS

    @EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
    object Registration {
        @SubscribeEvent
        fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            event.registerEntityRenderer(ModEntities.DISCHARGE_BOLT, ::DischargeBoltRenderer)
        }
    }
}
