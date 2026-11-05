package com.equi.channelingoverhaul.data

import com.equi.channelingoverhaul.advancement.ChargeConditions
import com.equi.channelingoverhaul.advancement.ChargeSource
import com.equi.channelingoverhaul.advancement.DischargeConditions
import com.equi.channelingoverhaul.advancement.DischargeStats
import com.equi.channelingoverhaul.advancement.HitKind
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The advancement tree of DESIGN.md and the matching of our triggers' conditions, decoded from the advancement files
 * as the game decodes them. Whether the advancements load and fire in a real world is a game test.
 */
class AdvancementDataTest {
    private val dir = File("src/main/resources/data/channeling_overhaul/advancement")

    private fun advancement(id: String): JsonObject = JsonParser.parseString(File(dir, "$id.json").readText()).asJsonObject

    private fun lang(code: String): JsonObject =
        JsonParser.parseString(File("src/main/resources/assets/channeling_overhaul/lang/$code.json").readText()).asJsonObject

    /** id to parent, frame and icon */
    private val tree = mapOf(
        "root" to Triple(null, "task", "minecraft:trident"),
        "fully_charged" to Triple("root", "task", "minecraft:copper_bulb"),
        "lightning_catcher" to Triple("fully_charged", "goal", "minecraft:lightning_rod"),
        "lord_of_storms" to Triple("lightning_catcher", "challenge", "minecraft:trident"),
        "chain_reaction" to Triple("root", "task", "minecraft:chain"),
        "bath_time" to Triple("chain_reaction", "goal", "minecraft:water_bucket"),
        "eight_links" to Triple("chain_reaction", "challenge", "minecraft:chain"),
        "hundred" to Triple("eight_links", "challenge", "minecraft:end_crystal"),
    )

    /** The conditions of the only criterion of [id], which must use [trigger]. */
    private fun conditions(id: String, trigger: String): JsonObject {
        val criteria = advancement(id).getAsJsonObject("criteria")
        assertEquals(1, criteria.size(), id)
        val criterion = criteria.entrySet().single().value.asJsonObject
        assertEquals(trigger, criterion.get("trigger").asString, id)
        return criterion.getAsJsonObject("conditions")
    }

    private fun charge(id: String): ChargeConditions =
        ChargeConditions.MAP_CODEC.codec().parse(JsonOps.INSTANCE, conditions(id, "channeling_overhaul:charge")).getOrThrow()

    private fun discharge(id: String): DischargeConditions =
        DischargeConditions.MAP_CODEC.codec().parse(JsonOps.INSTANCE, conditions(id, "channeling_overhaul:discharge")).getOrThrow()

    private fun stats(vararg hits: Pair<HitKind, Boolean>, damage: Double = 10.0) =
        DischargeStats().apply { hits.forEach { (kind, killed) -> hit(kind, damage, killed) } }

    @Test
    fun `the tree, frames and icons match DESIGN_md`() {
        assertEquals(tree.keys.map { "$it.json" }.toSet(), dir.list()!!.toSet())
        for ((id, node) in tree) {
            val (parent, frame, icon) = node
            val json = advancement(id)
            assertEquals(parent?.let { "channeling_overhaul:$it" }, json.get("parent")?.asString, id)
            val display = json.getAsJsonObject("display")
            assertEquals(frame, display.get("frame").asString, id)
            assertEquals(icon, display.getAsJsonObject("icon").get("id").asString, id)
        }
        // The challenge over a task with the same item glints: Lord of Storms over Storm Tamer, Eight Links over Chain Reaction
        for (id in listOf("lord_of_storms", "eight_links")) {
            val icon = advancement(id).getAsJsonObject("display").getAsJsonObject("icon")
            assertTrue(icon.getAsJsonObject("components").get("minecraft:enchantment_glint_override").asBoolean, id)
        }
    }

    @Test
    fun `only the root has the copper background, every advancement is announced and shown`() {
        for (id in tree.keys) {
            val display = advancement(id).getAsJsonObject("display")
            assertEquals(if (id == "root") "minecraft:textures/block/copper_block.png" else null, display.get("background")?.asString, id)
            assertTrue(display.get("show_toast").asBoolean && display.get("announce_to_chat").asBoolean, id)
            assertFalse(display.get("hidden").asBoolean, id)
        }
    }

    @Test
    fun `challenges give 50 experience, the rest nothing`() {
        for ((id, node) in tree) {
            val rewards = advancement(id).getAsJsonObject("rewards")
            if (node.second == "challenge") {
                assertEquals(setOf("experience"), rewards.keySet(), id)
                assertEquals(50, rewards.get("experience").asInt, id)
            } else {
                assertNull(rewards, id)
            }
        }
    }

    @Test
    fun `titles and descriptions are translated in all languages`() {
        val languages = listOf("en_us", "ru_ru", "ja_jp").map(::lang)
        for (id in tree.keys) {
            val display = advancement(id).getAsJsonObject("display")
            for (field in listOf("title", "description")) {
                val key = display.getAsJsonObject(field).get("translate").asString
                assertEquals("advancements.channeling_overhaul.$id.$field", key)
                assertTrue(languages.all { it.has(key) && it.get(key).asString.isNotBlank() }, key)
            }
        }
    }

    @Test
    fun `the description of Hundred names its threshold in damage and in hearts`() {
        val damage = "%.0f".format(discharge("hundred").maxDamage.min.get())
        val hearts = "%.0f".format(discharge("hundred").maxDamage.min.get() / 2)
        // Hearts as the vanilla Over-Overkill description words them in each language
        val wording = mapOf("en_us" to "$hearts hearts", "ru_ru" to "$hearts сердец", "ja_jp" to "ハート${hearts}個分")
        for ((code, inHearts) in wording) {
            val text = lang(code).get("advancements.channeling_overhaul.hundred.description").asString
            assertTrue(damage in text && inHearts in text, "$code: $text")
        }
    }

    @Test
    fun `Storm Tamer and Lord of Storms ask for a trident with Channeling, the lord at level 31`() {
        for ((id, levels) in listOf("root" to null, "lord_of_storms" to 31)) {
            val item = conditions(id, "minecraft:inventory_changed").getAsJsonArray("items").single().asJsonObject
            assertEquals("minecraft:trident", item.get("items").asString, id)
            val enchantment = item.getAsJsonObject("predicates").getAsJsonArray("minecraft:enchantments").single().asJsonObject
            assertEquals("minecraft:channeling", enchantment.get("enchantments").asString, id)
            assertEquals(levels, enchantment.getAsJsonObject("levels")?.get("min")?.asInt, id)
        }
    }

    @Test
    fun `Fully Charged takes crossing the full mark from any source`() {
        val conditions = charge("fully_charged")
        for (source in ChargeSource.entries) {
            assertTrue(conditions.matches(true, source), "$source")
            assertFalse(conditions.matches(false, source), "$source")
        }
    }

    @Test
    fun `Lightning Catcher takes a bolt into a rod, full or not, and nothing else`() {
        val conditions = charge("lightning_catcher")
        assertTrue(conditions.matches(false, ChargeSource.LIGHTNING_ROD))
        assertTrue(conditions.matches(true, ChargeSource.LIGHTNING_ROD))
        assertFalse(conditions.matches(true, ChargeSource.LIGHTNING))
        assertFalse(conditions.matches(true, ChargeSource.SKY))
    }

    @Test
    fun `Chain Reaction counts the primary target, the water and the chain together`() {
        val conditions = discharge("chain_reaction")
        assertTrue(conditions.matches(stats(HitKind.PRIMARY to false, HitKind.WATER to false, HitKind.CHAIN to false, HitKind.CHAIN to false)))
        assertFalse(conditions.matches(stats(HitKind.PRIMARY to true, HitKind.WATER to true, HitKind.CHAIN to true)))
    }

    @Test
    fun `Bath Time counts only kills through the water`() {
        val conditions = discharge("bath_time")
        fun water(kills: Int) = Array(kills) { HitKind.WATER to true }
        assertTrue(conditions.matches(stats(HitKind.PRIMARY to false, *water(5))))
        // Five kills, but one by the primary hit and one by the chain
        assertFalse(conditions.matches(stats(HitKind.PRIMARY to true, *water(3), HitKind.CHAIN to true)))
        // Five through the water, but one survived
        assertFalse(conditions.matches(stats(HitKind.PRIMARY to false, *water(4), HitKind.WATER to false)))
    }

    @Test
    fun `Eight Links needs eight hops, water hits don't count as hops`() {
        val conditions = discharge("eight_links")
        assertTrue(conditions.matches(stats(HitKind.PRIMARY to false, *Array(8) { HitKind.CHAIN to false })))
        assertFalse(conditions.matches(stats(HitKind.PRIMARY to false, *Array(7) { HitKind.CHAIN to false }, HitKind.WATER to false)))
    }

    @Test
    fun `Hundred needs a computed D of 100 on one target, not in total`() {
        val conditions = discharge("hundred")
        assertTrue(conditions.matches(stats(HitKind.PRIMARY to false, damage = 100.0)))
        assertFalse(conditions.matches(stats(HitKind.PRIMARY to false, damage = 99.99)))
        assertFalse(conditions.matches(stats(*Array(4) { HitKind.CHAIN to false }, damage = 60.0)))
    }

    @Test
    fun `a missing field matches anything`() {
        val charge = ChargeConditions.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonObject()).getOrThrow()
        assertTrue(ChargeSource.entries.all { charge.matches(true, it) && charge.matches(false, it) })
        val discharge = DischargeConditions.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonObject()).getOrThrow()
        assertTrue(discharge.matches(DischargeStats()))
    }

    @Test
    fun `discharge stats count every hit once, the hops and the largest damage`() {
        val stats = DischargeStats()
        stats.hit(HitKind.PRIMARY, 24.0, killed = true)
        stats.hit(HitKind.WATER, 5.0, killed = true)
        stats.hit(HitKind.WATER, 4.0, killed = false)
        stats.hit(HitKind.CHAIN, 14.0, killed = true)
        stats.hit(HitKind.CHAIN, 9.0, killed = false)
        assertEquals(5, stats.targetsHit)
        assertEquals(3, stats.kills)
        assertEquals(1, stats.waterKills)
        assertEquals(2, stats.hops)
        assertEquals(24.0, stats.maxDamage)
    }

    @Test
    fun `the challenges are reachable at the levels DESIGN_md names`() {
        val p = ChargeParams()
        val hops = discharge("eight_links").hops.min.get()
        assertEquals(hops, ChargeMath.chainJumps(24, p))
        assertTrue(ChargeMath.chainJumps(23, p) < hops)
        val damage = discharge("hundred").maxDamage.min.get()
        fun best(level: Int, m: Double) = ChargeMath.dischargeDamage(ChargeMath.capacity(level, p), m, p)
        assertTrue(best(27, 1.0) >= damage && best(26, 1.0) < damage)
        assertTrue(best(13, 2.0) >= damage && best(12, 2.0) < damage)
    }
}
