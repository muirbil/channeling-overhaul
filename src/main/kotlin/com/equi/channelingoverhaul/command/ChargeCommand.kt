package com.equi.channelingoverhaul.command

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.data.TridentCharge
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Debug command for the trident in the main hand:
 * `/channeling charge get | set <amount> | fill | clear`.
 * `get` also prints the level, Qmax and R, to check that the server config is read.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object ChargeCommand {
    private val NO_TRIDENT = SimpleCommandExceptionType(
        Component.translatable(
            "commands.channeling_overhaul.charge.no_trident",
            Component.translatable("enchantment.minecraft.channeling"),
        )
    )

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("channeling")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("charge")
                        .then(Commands.literal("get").executes { get(it.source) })
                        .then(
                            Commands.literal("set").then(
                                Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                    .executes { set(it.source, DoubleArgumentType.getDouble(it, "amount")) }
                            )
                        )
                        .then(Commands.literal("fill").executes { set(it.source, Double.MAX_VALUE) })
                        .then(Commands.literal("clear").executes { set(it.source, 0.0) })
                )
        )
    }

    private class Held(val stack: ItemStack, val level: Int)

    private fun held(source: CommandSourceStack): Held {
        val stack = source.playerOrException.mainHandItem
        val level = TridentCharge.level(stack, source.registryAccess())
        if (level <= 0) throw NO_TRIDENT.create()
        return Held(stack, level)
    }

    private fun get(source: CommandSourceStack): Int {
        val held = held(source)
        val charge = TridentCharge.read(held.stack, source.level.gameTime)
        val config = if (ModConfig.SPEC.isLoaded) "server" else "defaults"
        source.sendSuccess({
            Component.translatable(
                "commands.channeling_overhaul.charge.get",
                Component.translatable("enchantment.minecraft.channeling"),
                held.level,
                format(charge),
                format(TridentCharge.capacity(held.level)),
                format(TridentCharge.radius(held.level)),
                Component.translatable("commands.channeling_overhaul.charge.config.$config"),
            )
        }, false)
        return charge.roundToInt()
    }

    /** Sets the charge, capped by Qmax. 0 removes the component. */
    private fun set(source: CommandSourceStack, amount: Double): Int {
        val held = held(source)
        val capacity = TridentCharge.capacity(held.level)
        val charge = min(amount, capacity)
        TridentCharge.write(held.stack, charge, source.level.gameTime)
        source.sendSuccess({
            if (charge > 0.0) {
                Component.translatable("commands.channeling_overhaul.charge.set", format(charge), format(capacity))
            } else {
                Component.translatable("commands.channeling_overhaul.charge.clear")
            }
        }, true)
        return charge.roundToInt()
    }

    private fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)
}
