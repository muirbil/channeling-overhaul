package com.equi.channelingoverhaul.advancement

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.criterion.ContextAwarePredicate
import net.minecraft.advancements.criterion.EntityPredicate
import net.minecraft.advancements.criterion.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import java.util.Optional

/** Where a charge increase of a stuck trident came from. */
enum class ChargeSource(private val id: String) : StringRepresentable {
    /** Charging from the sky, with the antenna */
    SKY("sky"),
    /** A lightning bolt anywhere but a lightning rod */
    LIGHTNING("lightning"),
    /** A lightning bolt into a lightning rod */
    LIGHTNING_ROD("lightning_rod");

    override fun getSerializedName() = id

    companion object {
        val CODEC: Codec<ChargeSource> = StringRepresentable.fromEnum { entries.toTypedArray() }
    }
}

/** Conditions of `channeling_overhaul:charge` besides `player`; a missing field matches anything. */
data class ChargeConditions(val full: Optional<Boolean>, val source: Optional<ChargeSource>) {
    /** [full]: this increase crossed the "full" mark. */
    fun matches(full: Boolean, source: ChargeSource): Boolean =
        this.full.map { it == full }.orElse(true) && this.source.map { it == source }.orElse(true)

    companion object {
        val MAP_CODEC: MapCodec<ChargeConditions> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                Codec.BOOL.optionalFieldOf("full").forGetter(ChargeConditions::full),
                ChargeSource.CODEC.optionalFieldOf("source").forGetter(ChargeConditions::source),
            ).apply(i, ::ChargeConditions)
        }
    }
}

/** `channeling_overhaul:charge`: a stuck trident of the player gained charge from the sky or a lightning bolt. */
class ChargeTrigger : SimpleCriterionTrigger<ChargeTrigger.Instance>() {
    override fun codec(): Codec<Instance> = Instance.CODEC

    fun trigger(player: ServerPlayer, full: Boolean, source: ChargeSource) =
        trigger(player) { it.conditions.matches(full, source) }

    class Instance(private val playerPredicate: Optional<ContextAwarePredicate>, val conditions: ChargeConditions) :
        SimpleCriterionTrigger.SimpleInstance {
        override fun player() = playerPredicate

        companion object {
            val CODEC: Codec<Instance> = RecordCodecBuilder.create { i ->
                i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ChargeConditions.MAP_CODEC.forGetter(Instance::conditions),
                ).apply(i, ::Instance)
            }
        }
    }
}
