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
    fun `every glow stage of Visuals has a model over the vanilla sprite`() {
        for (stage in 1..Visuals.GLOW_THRESHOLDS.size) {
            val textures = json("channeling_overhaul/models/item/trident_charge_$stage.json").getAsJsonObject("textures")
            assertEquals("minecraft:item/trident", textures.get("layer0").asString)
            val overlay = textures.get("layer1").asString
            assertTrue(File(assets, "${overlay.substringBefore(':')}/textures/${overlay.substringAfter(':')}.png").isFile, overlay)
        }
    }

    @Test
    fun `every sound points at a vanilla event and has a subtitle in all languages`() {
        val sounds = json("channeling_overhaul/sounds.json")
        assertEquals(
            setOf("trident.full_charge", "discharge.chain", "discharge.weak", "discharge.horn", "discharge.distant_thunder"),
            sounds.keySet(),
        )
        val en = json("channeling_overhaul/lang/en_us.json")
        val ru = json("channeling_overhaul/lang/ru_ru.json")
        val ja = json("channeling_overhaul/lang/ja_jp.json")
        for ((name, value) in sounds.entrySet()) {
            val sound = value.asJsonObject
            val subtitle = sound.get("subtitle").asString
            assertTrue(en.has(subtitle) && ru.has(subtitle) && ja.has(subtitle), "$name: no subtitle $subtitle")
            val event = sound.getAsJsonArray("sounds")[0].asJsonObject
            assertEquals("event", event.get("type").asString, name)
            assertTrue(event.get("name").asString.startsWith("minecraft:"), name)
        }
    }
}
