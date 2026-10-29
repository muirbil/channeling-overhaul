package com.equi.channelingoverhaul.command

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.StationReading
import com.equi.channelingoverhaul.charge.StuckTridentCharger
import com.equi.channelingoverhaul.charge.isStuck
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.registry.ModAttachments
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.Locale

/**
 * `/channeling station`: what charges the closest stuck trident with Channeling within 8 blocks.
 * Rescans its sources first, so the output matches the blocks around it right now.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object StationCommand {
    private const val SEARCH_RADIUS = 8.0

    private val NO_TRIDENT = SimpleCommandExceptionType(
        Component.translatable(
            "commands.channeling_overhaul.station.no_trident",
            Component.translatable("enchantment.minecraft.channeling"),
            SEARCH_RADIUS.toInt(),
        )
    )

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("channeling")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("station").executes { station(it.source) })
        )
    }

    private fun station(source: CommandSourceStack): Int {
        val level = source.level
        val origin = source.position
        val p = ModConfig.params
        val trident = level.getEntitiesOfClass(ThrownTrident::class.java, AABB.ofSize(origin, 2 * SEARCH_RADIUS, 2 * SEARCH_RADIUS, 2 * SEARCH_RADIUS)) {
            it.isStuck && it.distanceToSqr(origin) <= SEARCH_RADIUS * SEARCH_RADIUS &&
                TridentCharge.level(it.pickupItemStackOrigin, level.registryAccess()) > 0
        }.minByOrNull { it.distanceToSqr(origin) } ?: throw NO_TRIDENT.create()

        val stack = trident.pickupItemStackOrigin
        val channeling = TridentCharge.level(stack, level.registryAccess())
        val state = trident.getData(ModAttachments.CHARGER)
        StuckTridentCharger.rescan(trident, channeling, state, level.gameTime, p)
        val reading = StationReading.of(trident, channeling, state.scan, p)
        val antenna = reading.antenna

        val lines = listOf(
            Component.translatable(
                "commands.channeling_overhaul.station.trident",
                Component.translatable("enchantment.minecraft.channeling"),
                channeling,
                String.format(Locale.ROOT, "%.1f, %.1f, %.1f", trident.x, trident.y, trident.z),
                format(reading.radius),
            ),
            Component.translatable(
                "commands.channeling_overhaul.station.sky",
                format(reading.sky),
                Component.translatable("commands.channeling_overhaul.station.sky.${if (reading.canSeeSky) "open" else "covered"}"),
                Component.translatable("commands.channeling_overhaul.station.precipitation.${reading.precipitation.name.lowercase()}"),
                Component.translatable("commands.channeling_overhaul.station.weather.${reading.weather.name.lowercase()}"),
            ),
            Component.translatable(
                "commands.channeling_overhaul.station.antenna",
                format(antenna.total),
                format(p.antennaMax),
                format(antenna.copper),
                reading.scan.copper.size,
                format(antenna.rods),
                reading.scan.rods.size,
                format(antenna.water),
                reading.scan.water.size,
            ),
            Component.translatable(
                "commands.channeling_overhaul.station.inflow",
                format(reading.inflow),
                format(TridentCharge.stored(stack)),
                format(ChargeMath.capacity(channeling, p)),
            ),
        )
        source.sendSuccess({ lines.reduce { all, line -> all.copy().append("\n").append(line) } }, false)
        return channeling
    }

    private fun format(value: Double) = String.format(Locale.ROOT, "%.2f", value)
}
