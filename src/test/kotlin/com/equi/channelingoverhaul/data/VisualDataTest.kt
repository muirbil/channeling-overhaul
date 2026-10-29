package com.equi.channelingoverhaul.data

import com.equi.channelingoverhaul.core.Visuals
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Assets of stage 8: the glow stages of the trident item and the sounds with their subtitles. */
class VisualDataTest {
    private val assets = File("src/main/resources/assets")

    private fun json(path: String): JsonObject = JsonParser.parseString(File(assets, path).readText()).asJsonObject

    @Test
    fun `the trident item glows in the steps of Visuals, over the vanilla sprite`() {
        val flat = json("minecraft/items/trident.json").getAsJsonObject("model").getAsJsonArray("cases")[0].asJsonObject
        assertEquals(listOf("gui", "ground", "fixed", "on_shelf"), flat.getAsJsonArray("when").map { it.asString })
        val dispatch = flat.getAsJsonObject("model")
        assertEquals("minecraft:range_dispatch", dispatch.get("type").asString)
        assertEquals("channeling_overhaul:charge", dispatch.get("property").asString)
        assertEquals("minecraft:item/trident", dispatch.getAsJsonObject("fallback").get("model").asString)
        val entries = dispatch.getAsJsonArray("entries").map { it.asJsonObject }
        assertEquals(Visuals.GLOW_THRESHOLDS, entries.map { it.get("threshold").asDouble })
        for (entry in entries) {
            val id = entry.getAsJsonObject("model").get("model").asString
            val model = json("${id.substringBefore(':')}/models/${id.substringAfter(':')}.json")
            val textures = model.getAsJsonObject("textures")
            assertEquals("minecraft:item/trident", textures.get("layer0").asString)
            val overlay = textures.get("layer1").asString
            assertTrue(File(assets, "${overlay.substringBefore(':')}/textures/${overlay.substringAfter(':')}.png").isFile, overlay)
        }
    }

    @Test
    fun `every sound points at a vanilla event and has a subtitle in both languages`() {
        val sounds = json("channeling_overhaul/sounds.json")
        assertEquals(
            setOf("trident.full_charge", "discharge.chain", "discharge.weak", "discharge.horn", "discharge.distant_thunder"),
            sounds.keySet(),
        )
        val en = json("channeling_overhaul/lang/en_us.json")
        val ru = json("channeling_overhaul/lang/ru_ru.json")
        for ((name, value) in sounds.entrySet()) {
            val sound = value.asJsonObject
            val subtitle = sound.get("subtitle").asString
            assertTrue(en.has(subtitle) && ru.has(subtitle), "$name: no subtitle $subtitle")
            val event = sound.getAsJsonArray("sounds")[0].asJsonObject
            assertEquals("event", event.get("type").asString, name)
            assertTrue(event.get("name").asString.startsWith("minecraft:"), name)
        }
    }
}
