package com.equi.channelingoverhaul.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/** Data of the discharge: armor bonuses, damage types and their tags, death messages. Registry coverage is a game test. */
class DischargeDataTest {
    private fun json(path: String): JsonObject =
        JsonParser.parseString(File("src/main/resources/$path").readText()).asJsonObject

    private val types = listOf("discharge", "discharge_chain", "discharge_water")

    @Test
    fun `armor bonuses match DESIGN_md`() {
        val values = json("data/channeling_overhaul/data_maps/item/armor_conductivity.json").getAsJsonObject("values")
        val perMaterial = mapOf(
            "golden" to 0.15, "copper" to 0.15, "netherite" to 0.12, "iron" to 0.10, "chainmail" to 0.10,
            "leather" to 0.0, "diamond" to 0.0,
        )
        val expected = perMaterial.flatMap { (material, bonus) ->
            listOf("helmet", "chestplate", "leggings", "boots").map { "minecraft:${material}_$it" to bonus }
        }.toMap() + ("minecraft:turtle_helmet" to 0.0)
        val actual = values.entrySet().associate { (item, value) -> item to value.asJsonObject.get("bonus").asDouble }
        assertEquals(expected, actual)
    }

    @Test
    fun `damage types are data with the fields from DESIGN_md`() {
        for (type in types) {
            val json = json("data/channeling_overhaul/damage_type/$type.json")
            assertEquals("channeling_overhaul.$type", json.get("message_id").asString)
            assertEquals(0.1, json.get("exhaustion").asDouble)
            assertEquals("when_caused_by_living_non_player", json.get("scaling").asString)
        }
    }

    @Test
    fun `damage types are lightning, skip the hurt cooldown and add no knockback`() {
        for (tag in listOf("is_lightning", "bypasses_cooldown", "no_knockback")) {
            val json = json("data/minecraft/tags/damage_type/$tag.json")
            assertEquals(false, json.get("replace").asBoolean, tag)
            assertEquals(types.map { "channeling_overhaul:$it" }, json.getAsJsonArray("values").map { it.asString }, tag)
        }
    }

    private fun lang(code: String) = json("assets/channeling_overhaul/lang/$code.json")

    /** The arguments a string reads, in order of their numbers: `%s` takes the next one, `%2$s` a fixed one. */
    private fun arguments(text: String): List<Int> {
        var next = 0
        return Regex("""%(?:(\d+)\$)?s""").findAll(text)
            .map { it.groupValues[1].toIntOrNull() ?: ++next }
            .sorted().toList()
    }

    @Test
    fun `all languages have the same keys, with every death message`() {
        val en = lang("en_us").keySet()
        assertEquals(en, lang("ru_ru").keySet())
        assertEquals(en, lang("ja_jp").keySet())
        val base = "death.attack.channeling_overhaul"
        val deaths = types.flatMap { listOf("$base.$it", "$base.$it.player") } +
            listOf("item", "overcharged", "conductor").map { "$base.discharge.$it" }
        assertTrue(en.containsAll(deaths), "missing: ${deaths - en}")
    }

    @ParameterizedTest
    @ValueSource(strings = ["ru_ru", "ja_jp"])
    fun `a translation reads the same arguments as English`(code: String) {
        val en = lang("en_us")
        val translated = lang(code)
        for (key in en.keySet()) {
            val text = translated.get(key).asString
            assertEquals(arguments(en.get(key).asString), arguments(text), "$code: $key")
            assertTrue(Regex("%(?!(\\d+\\$)?s)").find(text) == null, "$code: $key has a stray %: $text")
        }
    }
}
