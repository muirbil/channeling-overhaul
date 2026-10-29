package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.DischargeRules
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.registry.ModDamageTypes
import com.equi.channelingoverhaul.registry.ModDataMaps
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.OwnableEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Who started a discharge, kept as ids rather than entities: the chain runs over several ticks,
 * while the owner may log out and the trident may be picked up.
 */
class DischargeOrigin(
    val dimension: ResourceKey<Level>,
    val ownerId: UUID?,
    val ownerIsPlayer: Boolean,
    /** Scoreboard team of the owner when the trident hit */
    val ownerTeam: String?,
    val tridentId: UUID,
) {
    /** The owner if still around, in any dimension for a player. */
    fun owner(level: ServerLevel): Entity? = ownerId?.let { level.server.playerList.getPlayer(it) ?: level.getEntity(it) }

    fun trident(level: ServerLevel): Entity? = level.getEntity(tridentId)

    companion object {
        fun of(level: ServerLevel, trident: ThrownTrident): DischargeOrigin {
            val owner = trident.owner
            return DischargeOrigin(level.dimension(), owner?.uuid, owner is Player, owner?.team?.name, trident.uuid)
        }
    }
}

/** Targets of the chain and the water: who may be hit and how vulnerable they are. */
object DischargeTargets {
    /** `data/channeling_overhaul/tags/entity_type/discharge_immune.json`: never hit by the chain or the water. */
    val DISCHARGE_IMMUNE: TagKey<EntityType<*>> =
        TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, "discharge_immune"))

    /** Bonus of each worn armor piece that has one in the `armor_conductivity` data map. */
    fun armorBonuses(target: LivingEntity): List<Double> =
        EquipmentSlot.entries.filter { it.isArmor }
            .map(target::getItemBySlot)
            .filterNot(ItemStack::isEmpty)
            .mapNotNull { it.typeHolder().getData(ModDataMaps.ARMOR_CONDUCTIVITY) }

    /** M = min(2, 1 + armor bonuses + 0.25 when wet). */
    fun vulnerability(target: LivingEntity, p: ChargeParams): Double =
        ChargeMath.vulnerability(armorBonuses(target).sum(), target.isInWaterOrRain, p)

    /**
     * Whether a chain hop or the water may hit [entity]. Never the owner, the owner's tamed animals and teammates,
     * mobs tagged `discharge_immune`, spectators, armor stands or anyone invulnerable to [source].
     * Players only when PvP is on and [DischargeRules.chainHitsPlayers]; from a mob's trident always.
     */
    fun canHit(level: ServerLevel, entity: LivingEntity, origin: DischargeOrigin, source: DamageSource, rules: DischargeRules): Boolean {
        if (!entity.isAlive || entity.isSpectator || entity is ArmorStand) return false
        if (entity.typeHolder().`is`(DISCHARGE_IMMUNE) || entity.isInvulnerableTo(level, source)) return false
        val ownerId = origin.ownerId
        if (ownerId != null && (entity.uuid == ownerId || (entity as? OwnableEntity)?.ownerReference?.uuid == ownerId)) return false
        if (origin.ownerTeam != null && entity.team?.name == origin.ownerTeam) return false
        if (entity is Player && origin.ownerIsPlayer && !(level.isPvpAllowed && rules.chainHitsPlayers)) return false
        return true
    }

    /** Damage of a chain hop or a water hit. Without its owner around, the discharge plays out with no attacker. */
    fun source(level: ServerLevel, type: ResourceKey<DamageType>, origin: DischargeOrigin) =
        source(level, type, origin.trident(level), origin.owner(level))

    /**
     * Chain and water always get the plain message of their type: "vaporized" and "metal conducts electricity"
     * belong to the direct discharge only.
     */
    fun source(level: ServerLevel, type: ResourceKey<DamageType>, trident: Entity?, owner: Entity?) =
        DischargeDamageSource(ModDamageTypes.holder(level, type), trident, owner, false, false, null)

    /** The owner, if it is a player on the server: the one who gets the debug output. */
    fun debugReceiver(level: ServerLevel, origin: DischargeOrigin): ServerPlayer? =
        origin.ownerId?.let(level.server.playerList::getPlayer)
}
