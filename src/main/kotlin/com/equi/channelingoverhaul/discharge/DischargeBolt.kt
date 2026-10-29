package com.equi.channelingoverhaul.discharge

import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * The bolt of a strong discharge, only for the eye. A vanilla `LightningBolt`, even a visual-only one,
 * still thunders for everyone within 16 chunks, cleans copper and powers a lightning rod where it strikes;
 * this one does nothing to the world and makes no sound. It lives [LIFETIME_TICKS] ticks and is not saved.
 * The client draws it with the vanilla zigzag ([com.equi.channelingoverhaul.client.DischargeBoltRenderer]).
 * Not being a `LightningBolt`, it is not a lightning strike for anyone listening for those.
 */
class DischargeBolt(type: EntityType<out DischargeBolt>, level: Level) : Entity(type, level) {
    /** Shape of the zigzag. Each side rolls its own, as vanilla does for its bolts. */
    val seed: Long = random.nextLong()
    private var age = 0

    override fun tick() {
        super.tick()
        if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard()
    }

    /** As far as a vanilla bolt. */
    override fun shouldRenderAtSqrDistance(distance: Double): Boolean {
        val size = 64.0 * getViewScale()
        return distance < size * size
    }

    override fun defineSynchedData(entityData: SynchedEntityData.Builder) {}

    override fun readAdditionalSaveData(input: ValueInput) {}

    override fun addAdditionalSaveData(output: ValueOutput) {}

    override fun hurtServer(level: ServerLevel, source: DamageSource, damage: Float): Boolean = false

    companion object {
        const val LIFETIME_TICKS = 8
    }
}
