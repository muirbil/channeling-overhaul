package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Sounds of the mod. `assets/channeling_overhaul/sounds.json` points each of them at a vanilla sound event,
 * so a resource pack can swap any of them without touching code.
 */
object ModSounds {
    val REGISTRY: DeferredRegister<SoundEvent> = DeferredRegister.create(Registries.SOUND_EVENT, ChannelingOverhaul.ID)

    /** A stuck trident reached Qmax: `block.amethyst_block.chime` */
    val FULL_CHARGE = sound("trident.full_charge")
    /** A chain hop: `entity.lightning_bolt.impact` */
    val CHAIN_CRACKLE = sound("discharge.chain")
    /** A weak discharge, below 50% Qmax: `entity.lightning_bolt.impact` */
    val WEAK_DISCHARGE = sound("discharge.weak")
    /** The horn of a strong discharge: `item.trident.thunder` */
    val HORN = sound("discharge.horn")
    /** `distantThunder`: thunder far away, for the whole dimension: `entity.lightning_bolt.thunder` */
    val DISTANT_THUNDER = sound("discharge.distant_thunder")

    private fun sound(name: String): DeferredHolder<SoundEvent, SoundEvent> =
        REGISTRY.register(name) { id -> SoundEvent.createVariableRangeEvent(id) }
}
