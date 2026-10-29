package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.config.DeathMessageMode
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.registry.ModDamageTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.OwnableEntity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.gamerules.GameRules
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent

/**
 * Tells everyone that a mob died of a discharge, its chain or its water (config `deathMessages`).
 * Vanilla tells everyone of a player's death and the owner of a tamed animal's: those are not repeated.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object DeathAnnouncer {
    private val TYPES = listOf(ModDamageTypes.DISCHARGE, ModDamageTypes.DISCHARGE_CHAIN, ModDamageTypes.DISCHARGE_WATER)

    /** Last, so a death another listener cancels is not announced. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDeath(event: LivingDeathEvent) {
        val victim = event.entity
        val level = victim.level() as? ServerLevel ?: return
        if (TYPES.none { event.source.`is`(it) }) return
        val showDeathMessages = level.gameRules.get(GameRules.SHOW_DEATH_MESSAGES)
        val recipients = recipients(victim, ModConfig.rules.deathMessages, showDeathMessages, level.server.playerList.players)
        if (recipients.isEmpty()) return
        val message = victim.combatTracker.deathMessage
        for (player in recipients) player.sendSystemMessage(message)
    }

    /**
     * Who hears of [victim]'s death besides those vanilla tells. `named`: for mobs with a custom name and tamed
     * animals, `all`: for any mob. Never for players, never against `show_death_messages`. The owner of a tamed
     * animal is left out when vanilla tells them itself, which `TamableAnimal.die` does right after this event.
     */
    fun recipients(victim: LivingEntity, mode: DeathMessageMode, showDeathMessages: Boolean, players: List<ServerPlayer>): List<ServerPlayer> {
        if (!showDeathMessages || mode == DeathMessageMode.VANILLA || victim is Player) return emptyList()
        val tamed = (victim as? OwnableEntity)?.ownerReference != null
        if (mode == DeathMessageMode.NAMED && !victim.hasCustomName() && !tamed) return emptyList()
        val toldByVanilla = (victim as? TamableAnimal)?.owner as? ServerPlayer
        return players.filter { it !== toldByVanilla }
    }
}
