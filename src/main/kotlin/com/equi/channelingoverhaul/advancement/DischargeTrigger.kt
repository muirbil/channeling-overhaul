package com.equi.channelingoverhaul.advancement

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.criterion.ContextAwarePredicate
import net.minecraft.advancements.criterion.EntityPredicate
import net.minecraft.advancements.criterion.MinMaxBounds
import net.minecraft.advancements.criterion.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/** Conditions of `channeling_overhaul:discharge` besides `player`; a missing field matches anything. */
data class DischargeConditions(
    val targetsHit: MinMaxBounds.Ints,
    val kills: MinMaxBounds.Ints,
    val waterKills: MinMaxBounds.Ints,
    val hops: MinMaxBounds.Ints,
    val maxDamage: MinMaxBounds.Doubles,
) {
    fun matches(stats: DischargeStats): Boolean =
        targetsHit.matches(stats.targetsHit) && kills.matches(stats.kills) && waterKills.matches(stats.waterKills) &&
            hops.matches(stats.hops) && maxDamage.matches(stats.maxDamage)

    companion object {
        val MAP_CODEC: MapCodec<DischargeConditions> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                MinMaxBounds.Ints.CODEC.optionalFieldOf("targets_hit", MinMaxBounds.Ints.ANY).forGetter(DischargeConditions::targetsHit),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("kills", MinMaxBounds.Ints.ANY).forGetter(DischargeConditions::kills),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("water_kills", MinMaxBounds.Ints.ANY).forGetter(DischargeConditions::waterKills),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("hops", MinMaxBounds.Ints.ANY).forGetter(DischargeConditions::hops),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("max_damage", MinMaxBounds.Doubles.ANY).forGetter(DischargeConditions::maxDamage),
            ).apply(i, ::DischargeConditions)
        }
    }
}

/** `channeling_overhaul:discharge`: a discharge of the player's trident is over, with its [DischargeStats]. */
class DischargeTrigger : SimpleCriterionTrigger<DischargeTrigger.Instance>() {
    override fun codec(): Codec<Instance> = Instance.CODEC

    fun trigger(player: ServerPlayer, stats: DischargeStats) = trigger(player) { it.conditions.matches(stats) }

    class Instance(private val playerPredicate: Optional<ContextAwarePredicate>, val conditions: DischargeConditions) :
        SimpleCriterionTrigger.SimpleInstance {
        override fun player() = playerPredicate

        companion object {
            val CODEC: Codec<Instance> = RecordCodecBuilder.create { i ->
                i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    DischargeConditions.MAP_CODEC.forGetter(Instance::conditions),
                ).apply(i, ::Instance)
            }
        }
    }
}
