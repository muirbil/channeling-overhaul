package com.equi.channelingoverhaul.client

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.data.TridentCharge
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import kotlin.math.roundToInt

/** "Charge: 23 / 40" under tridents with Channeling. */
@EventBusSubscriber(modid = ChannelingOverhaul.ID, value = [Dist.CLIENT])
object ChargeTooltip {
    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val level = event.entity?.level() ?: return
        val registries = event.context.registries() ?: level.registryAccess()
        val channeling = TridentCharge.level(event.itemStack, registries)
        if (channeling <= 0) return

        val charge = TridentCharge.peek(event.itemStack, level.gameTime)
        val capacity = TridentCharge.capacity(channeling)
        event.toolTip.add(
            Component.translatable("tooltip.channeling_overhaul.charge", charge.roundToInt(), capacity.roundToInt())
                .withStyle(ChatFormatting.AQUA)
        )
    }
}
