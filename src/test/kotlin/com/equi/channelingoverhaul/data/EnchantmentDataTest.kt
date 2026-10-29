package com.equi.channelingoverhaul.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.shadowsoffire.apothic_attributes.repack.evalex.Expression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal

/** Checks the enchantment data pack against DESIGN.md, the vanilla file and Apothic Enchanting's evaluator. */
class EnchantmentDataTest {
    private fun ours(path: String): JsonObject =
        JsonParser.parseString(File("src/main/resources/$path").readText()).asJsonObject

    private val enchantment = ours("data/minecraft/enchantment/channeling.json")
    private val info = ours("data/apothic_enchanting/data_maps/enchantment/enchantment_info.json")
        .getAsJsonObject("values").getAsJsonObject("minecraft:channeling")

    /** The vanilla file from the Minecraft jar on the classpath, not our override. */
    private val vanilla: JsonObject = javaClass.classLoader
        .getResources("data/minecraft/enchantment/channeling.json").toList()
        .single { it.protocol == "jar" && "minecraft" in it.path }
        .readText().let { JsonParser.parseString(it).asJsonObject }

    /** Same calls as Apothic Enchanting's ExpressionPowerFunction, one expression reused for every level. */
    private val minPower: (Int) -> Int = run {
        val expression = Expression(info.getAsJsonObject("min_power").get("expression").asString)
        // toInt() is BigDecimal.intValue(), as in Apothic Enchanting
        val fn = { level: Int -> expression.setVariable("x", BigDecimal(level)).eval().toInt() }
        fn
    }

    @Test
    fun `min_power hits the anchor points`() {
        val anchors = mapOf(5 to 20, 10 to 40, 20 to 80, 25 to 100, 30 to 175, 31 to 190)
        assertEquals(anchors, anchors.mapValues { (level, _) -> minPower(level) })
    }

    @Test
    fun `min_power grows with every level and stays within the table maximum of 200`() {
        val powers = (1..31).map(minPower)
        assertTrue(powers.zipWithNext().all { (a, b) -> b > a }, "$powers")
        assertTrue(powers.all { it <= 200 }, "$powers")
    }

    @Test
    fun `data map caps the level at 31 and keeps loot at I`() {
        assertEquals(31, info.get("max_level").asInt)
        assertEquals(31, info.get("level_cap").asInt)
        assertEquals(1, info.get("max_loot_level").asInt)
    }

    @Test
    fun `max level matches the data map, so no level shows as over the maximum`() {
        assertEquals(info.get("max_level").asInt, enchantment.get("max_level").asInt)
    }

    @Test
    fun `costs are linear with a nonzero step`() {
        for (key in listOf("min_cost", "max_cost")) {
            val cost = enchantment.getAsJsonObject(key)
            assertTrue(cost.get("per_level_above_first").asInt > 0, key)
            assertEquals(setOf("base", "per_level_above_first"), cost.keySet(), key)
        }
    }

    @Test
    fun `everything else is the vanilla file`() {
        val changed = setOf("max_level", "min_cost", "max_cost")
        assertEquals(vanilla.keySet(), enchantment.keySet())
        for (key in vanilla.keySet() - changed) {
            assertEquals(vanilla.get(key), enchantment.get(key), key)
        }
    }
}
