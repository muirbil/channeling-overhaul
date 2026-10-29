package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.core.ConductorType
import com.equi.channelingoverhaul.core.Conductivity
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

object ModDataMaps {
    private val TYPE_CODEC: Codec<ConductorType> = Codec.STRING.comapFlatMap(
        { name ->
            ConductorType.entries.find { it.serializedName == name }
                ?.let { DataResult.success(it) }
                ?: DataResult.error { "Unknown conductor type: $name" }
        },
        ConductorType::serializedName,
    )

    private val CONDUCTIVITY_CODEC: Codec<Conductivity> = RecordCodecBuilder.create { instance ->
        instance.group(
            TYPE_CODEC.optionalFieldOf("type", ConductorType.COPPER).forGetter(Conductivity::type),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("conductivity", 1.0).forGetter(Conductivity::conductivity),
        ).apply(instance) { type, conductivity -> Conductivity(type, conductivity) }
    }

    /**
     * `data/channeling_overhaul/data_maps/block/conductivity.json`: copper with its conductivity c,
     * lightning rods and water for the antenna. Synced for the client particles of stage 8.
     */
    val CONDUCTIVITY: DataMapType<Block, Conductivity> = DataMapType
        .builder(Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, "conductivity"), Registries.BLOCK, CONDUCTIVITY_CODEC)
        .synced(CONDUCTIVITY_CODEC, false)
        .build()

    /**
     * `data/channeling_overhaul/data_maps/item/armor_conductivity.json`: bonus to the target's M for each worn piece.
     * Only the server reads it.
     */
    val ARMOR_CONDUCTIVITY: DataMapType<Item, Double> = DataMapType
        .builder(
            Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, "armor_conductivity"),
            Registries.ITEM,
            Codec.doubleRange(0.0, 10.0).fieldOf("bonus").codec(),
        )
        .build()

    fun register() {
        MOD_BUS.addListener(RegisterDataMapTypesEvent::class.java) {
            it.register(CONDUCTIVITY)
            it.register(ARMOR_CONDUCTIVITY)
        }
    }
}
