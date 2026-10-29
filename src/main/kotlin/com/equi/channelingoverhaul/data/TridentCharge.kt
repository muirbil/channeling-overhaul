package com.equi.channelingoverhaul.data

import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.registry.ModDataComponents
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.enchantment.Enchantments

/** Reads and writes [ChargeData] on trident stacks, with the numbers from the server config. */
object TridentCharge {
    /** Below this the component is removed instead of stored. */
    private const val EMPTY = 0.005

    /**
     * Channeling level of a trident, 0 for any other item.
     * Goes through NeoForge, so level bonuses from other mods (Apotheosis affixes) count.
     */
    fun level(stack: ItemStack, registries: HolderLookup.Provider): Int {
        if (stack.item !is TridentItem) return 0
        val channeling = registries.lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.CHANNELING).orElse(null)
            ?: return 0
        return stack.getEnchantmentLevel(channeling)
    }

    fun capacity(level: Int): Double = ChargeMath.capacity(level, ModConfig.params)

    fun radius(level: Int): Double = ChargeMath.radius(level, ModConfig.params)

    /** Charge as stored at its last update, without the leak since then. */
    fun stored(stack: ItemStack): Double = stack.get(ModDataComponents.CHARGE)?.charge?.toDouble() ?: 0.0

    /** Moves the timestamp to [gameTime] without leaking, for time the charge must not leak over. Server only. */
    fun stamp(stack: ItemStack, gameTime: Long) = write(stack, stored(stack), gameTime)

    /** Charge at [gameTime] without changing the stack: for display on the client and for a held stack in use. */
    fun peek(stack: ItemStack, gameTime: Long): Double =
        stack.get(ModDataComponents.CHARGE)?.chargeAt(gameTime, ModConfig.params) ?: 0.0

    /** Charge at [gameTime]. Stores the leaked value back, so the next read starts from now. Server only. */
    fun read(stack: ItemStack, gameTime: Long): Double {
        val data = stack.get(ModDataComponents.CHARGE) ?: return 0.0
        if (data.lastGameTime == gameTime) return data.charge.toDouble()
        val charge = data.chargeAt(gameTime, ModConfig.params)
        write(stack, charge, gameTime)
        return charge
    }

    /** Stores [charge] as of [gameTime]. Server only. */
    fun write(stack: ItemStack, charge: Double, gameTime: Long) {
        if (charge < EMPTY) {
            stack.remove(ModDataComponents.CHARGE)
        } else {
            stack.set(ModDataComponents.CHARGE, ChargeData(charge.toFloat(), gameTime))
        }
    }
}
