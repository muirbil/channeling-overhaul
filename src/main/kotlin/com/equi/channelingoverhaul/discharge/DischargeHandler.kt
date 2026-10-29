package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.core.Discharge
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.registry.ModDamageTypes
import com.equi.channelingoverhaul.registry.ModEntities
import com.equi.channelingoverhaul.registry.ModSounds
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack

/**
 * Discharge of a thrown trident into the mob it hit, then through the water around it ([WaterSpread])
 * and along the chain ([ChainScheduler]). `ThrownTridentMixin` calls it only after the trident's own hit
 * went through, so a shield, disabled PvP, a teammate or invulnerability leave the charge in the trident.
 */
object DischargeHandler {
    @JvmStatic
    fun onTridentHit(trident: ThrownTrident, target: LivingEntity) {
        val level = trident.level() as? ServerLevel ?: return
        val stack = trident.pickupItemStackOrigin
        val channeling = TridentCharge.level(stack, level.registryAccess())
        if (channeling <= 0) return
        val p = ModConfig.params
        val now = level.gameTime
        // The leak of the flight ends here
        val q = TridentCharge.read(stack, now)
        val qMax = ChargeMath.capacity(channeling, p)
        val discharge = ChargeMath.discharge(q, qMax, DischargeTargets.armorBonuses(target), target.isInWaterOrRain, p) ?: return
        TridentCharge.write(stack, 0.0, now)

        // The vanilla Channeling effect has run before us: in a thunderstorm it struck the target with a real bolt
        val vanillaLightning = level.getEntitiesOfClass(LightningBolt::class.java, target.boundingBox.inflate(1.0)) {
            it.tickCount == 0
        }.isNotEmpty()
        val origin = DischargeOrigin.of(level, trident)
        target.hurtServer(level, source(level, trident, discharge, stack), discharge.damage.toFloat())
        effects(level, target, discharge, vanillaLightning, p)
        if (ModConfig.rules.distantThunder && discharge.overcharged && channeling >= p.distantThunderMinLevel) {
            // Volume 10000 carries the sound 160 000 blocks: the whole dimension, like a real thunderbolt
            level.playSound(
                null, target.x, target.y, target.z, ModSounds.DISTANT_THUNDER, SoundSource.WEATHER,
                10_000.0f, 0.8f + level.random.nextFloat() * 0.2f,
            )
        }
        DischargeEffects.debug(
            level, origin, "chat.channeling_overhaul.discharge.debug",
            target.displayName, discharge.charge, discharge.vulnerability, discharge.damage,
        )

        // One "already hit" for the whole discharge: nobody takes a second hit from the water or the chain
        val hit = mutableSetOf(target.id)
        WaterSpread.spread(level, target, discharge.charge, channeling, origin, hit, p, ModConfig.rules)
        ChainScheduler.start(level, origin, discharge.charge, channeling, target, hit, p)
    }

    fun source(level: ServerLevel, trident: ThrownTrident, discharge: Discharge, stack: ItemStack) = DischargeDamageSource(
        ModDamageTypes.holder(level, ModDamageTypes.DISCHARGE),
        trident,
        trident.owner,
        discharge.overcharged,
        discharge.conductor,
        stack.get(DataComponents.CUSTOM_NAME),
    )

    /**
     * Strong: a [DischargeBolt] and the horn, heard by everyone around. Weak: only a quiet crackle.
     * When the vanilla effect has struck already, its bolt and horn are enough, a second pair would only double them.
     */
    private fun effects(level: ServerLevel, target: LivingEntity, discharge: Discharge, vanillaLightning: Boolean, p: ChargeParams) {
        if (!discharge.strong) {
            DischargeEffects.crackle(level, target, p, weak = true)
            return
        }
        if (vanillaLightning) return
        ModEntities.DISCHARGE_BOLT.create(level, EntitySpawnReason.TRIGGERED)?.let { bolt ->
            bolt.snapTo(target.position())
            level.addFreshEntity(bolt)
        }
        level.playSound(
            null, target.x, target.y, target.z, ModSounds.HORN, target.soundSource,
            discharge.hornVolume.toFloat(), 1.0f,
        )
    }
}
