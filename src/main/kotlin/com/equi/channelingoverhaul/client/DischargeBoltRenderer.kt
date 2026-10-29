package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.discharge.DischargeBolt
import com.equi.channelingoverhaul.registry.ModEntities
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LightningBoltRenderer
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/**
 * Draws a [DischargeBolt] as a vanilla lightning bolt. The vanilla renderer builds the zigzag from the
 * seed in its render state alone, so it draws ours as is, and follows vanilla if the geometry changes.
 */
class DischargeBoltRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<DischargeBolt, LightningBoltRenderState>(context) {
    private val zigzag = LightningBoltRenderer(context)

    override fun createRenderState() = LightningBoltRenderState()

    override fun extractRenderState(entity: DischargeBolt, state: LightningBoltRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.seed = entity.seed
    }

    override fun submit(state: LightningBoltRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) =
        zigzag.submit(state, poseStack, collector, camera)

    /** The bolt reaches 128 blocks up from a point: never cull it by its zero-size box. */
    override fun affectedByCulling(entity: DischargeBolt) = false

    @EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
    object Registration {
        @SubscribeEvent
        fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            event.registerEntityRenderer(ModEntities.DISCHARGE_BOLT, ::DischargeBoltRenderer)
        }
    }
}
