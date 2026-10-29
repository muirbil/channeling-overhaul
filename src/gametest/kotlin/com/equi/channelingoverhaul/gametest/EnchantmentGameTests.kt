package com.equi.channelingoverhaul.gametest

import dev.shadowsoffire.apothic_enchanting.ApothicEnchanting
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import java.util.stream.Stream

object EnchantmentGameTests {
    /** Channeling goes up to XXXI and the enchanting table gives the levels from DESIGN.md. */
    fun enchantmentData(helper: GameTestHelper) {
        val channeling = helper.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING)
        val info = ApothicEnchanting.getEnchInfo(channeling)

        helper.assertTrue(channeling.value().maxLevel == 31, "vanilla max_level is ${channeling.value().maxLevel}")
        helper.assertTrue(info.getMaxLevel(channeling) == 31, "Apothic max level is ${info.getMaxLevel(channeling)}")
        helper.assertTrue(info.getMaxLootLevel(channeling) == 1, "loot level is ${info.getMaxLootLevel(channeling)}")
        helper.assertTrue(info.levelCap() == 31, "level cap is ${info.levelCap()}")

        // Table power -> the level the enchanting table offers, as in the "Зачарование" section
        val expected = mapOf(19 to 4, 20 to 5, 40 to 10, 80 to 20, 100 to 25, 174 to 29, 175 to 30, 190 to 31, 200 to 31)
        val trident = ItemStack(Items.TRIDENT)
        val actual = expected.mapValues { (power, _) ->
            ApothEnchantmentHelper.getAvailableEnchantmentResults(power, trident, Stream.of(channeling))
                .singleOrNull()?.level() ?: 0
        }
        helper.assertTrue(actual == expected, "table levels by power: $actual")
        helper.succeed()
    }
}
