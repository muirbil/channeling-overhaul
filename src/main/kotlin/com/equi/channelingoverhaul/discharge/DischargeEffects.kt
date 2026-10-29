package com.equi.channelingoverhaul.discharge

import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.network.SparksPayload
import com.equi.channelingoverhaul.registry.ModSounds
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.Locale

/**
 * Effects of a discharge as the server starts them: sounds for everyone around, and [SparksPayload] for the
 * particles, which the client draws by its own particle settings (`client.ClientSparks`).
 */
object DischargeEffects {
    /** A zigzag arc of sparks from [from] to [to]. */
    fun arc(level: ServerLevel, from: Vec3, to: Vec3) =
        SparksPayload.send(level, from.lerp(to, 0.5), SparksPayload(SparksPayload.Kind.ARC, from, to))

    /** Sparks and bubbles all over [entity], for a hit through water. */
    fun waterHit(level: ServerLevel, entity: Entity) = burst(level, entity, SparksPayload.Kind.WATER)

    /** Sparks all over [entity], for a trident a lightning bolt charged. */
    fun burst(level: ServerLevel, entity: Entity, kind: SparksPayload.Kind = SparksPayload.Kind.BURST) {
        val box = entity.boundingBox
        SparksPayload.send(level, box.center, SparksPayload(kind, box.center, Vec3(box.xsize / 2, box.ysize / 2, box.zsize / 2)))
    }

    /** The quiet crackle of a chain hop, or of a weak discharge when [weak]. */
    fun crackle(level: ServerLevel, at: Entity, p: ChargeParams, weak: Boolean = false) {
        level.playSound(
            null, at.x, at.y, at.z, if (weak) ModSounds.WEAK_DISCHARGE else ModSounds.CHAIN_CRACKLE, at.soundSource,
            p.crackleVolume.toFloat(), p.cracklePitch.toFloat(),
        )
    }

    /** One line of `debug.dischargeChat` to the owner: translation [key] with numbers formatted to 2 decimals. */
    fun debug(level: ServerLevel, origin: DischargeOrigin, key: String, vararg args: Any) {
        if (!ModConfig.rules.debugChat) return
        debug(DischargeTargets.debugReceiver(level, origin), key, *args)
    }

    /** One line of `debug.dischargeChat` to [receiver], when the flag is on. */
    fun debug(receiver: ServerPlayer?, key: String, vararg args: Any) {
        if (!ModConfig.rules.debugChat || receiver == null) return
        val formatted = args.map { if (it is Double) String.format(Locale.ROOT, "%.2f", it) else it }
        receiver.sendSystemMessage(Component.translatable(key, *formatted.toTypedArray()))
    }
}
