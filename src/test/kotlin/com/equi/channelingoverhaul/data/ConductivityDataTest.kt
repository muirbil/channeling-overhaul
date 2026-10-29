package com.equi.channelingoverhaul.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import java.io.File

/** The `conductivity` data map and the copper tags it uses. Full registry coverage is a game test. */
class ConductivityDataTest {
    private fun json(path: String): JsonObject =
        JsonParser.parseString(File("src/main/resources/data/$path").readText()).asJsonObject

    private val values = json("channeling_overhaul/data_maps/block/conductivity.json").getAsJsonObject("values")

    private fun tag(stage: String) =
        json("channeling_overhaul/tags/block/copper/$stage.json").getAsJsonArray("values").map { it.asString }

    private val stages = listOf("unaffected", "exposed", "weathered", "oxidized")

    @Test
    fun `copper stages have the conductivity from DESIGN_md`() {
        val expected = mapOf("unaffected" to 1.0, "exposed" to 0.75, "weathered" to 0.5, "oxidized" to 0.25)
        val actual = expected.mapValues { (stage, _) ->
            values.getAsJsonObject("#channeling_overhaul:copper/$stage").get("conductivity").asDouble
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `lightning rods and water have their own types`() {
        assertEquals("lightning_rod", values.getAsJsonObject("#minecraft:lightning_rods").get("type").asString)
        assertEquals("water", values.getAsJsonObject("minecraft:water").get("type").asString)
    }

    @ParameterizedTest
    @ValueSource(strings = ["unaffected", "exposed", "weathered", "oxidized"])
    fun `every block of a stage is there waxed and unwaxed`(stage: String) {
        val blocks = tag(stage)
        val plain = blocks.filterNot { it.startsWith("minecraft:waxed_") }
        assertEquals(plain.map { it.replace("minecraft:", "minecraft:waxed_") }.toSet(), (blocks - plain.toSet()).toSet())
        val otherStages = stages.filter { it != stage && it != "unaffected" }
        assertTrue(blocks.none { block -> otherStages.any { it in block } }, "$stage: $blocks")
        if (stage != "unaffected") assertTrue(blocks.all { stage in it }, "$stage: $blocks")
    }

    @Test
    fun `all stages list the same block families and no lightning rods`() {
        val families = stages.map { stage ->
            tag(stage).map { it.removePrefix("minecraft:").removePrefix("waxed_").removePrefix("${stage}_") }
                .map { if (it == "copper" || it == "copper_block") "copper_block" else it }
                .toSet()
        }
        assertTrue(families.all { it == families[0] }, "$families")
        assertTrue(stages.flatMap(::tag).none { "lightning_rod" in it })
    }
}
