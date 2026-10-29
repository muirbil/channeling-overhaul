package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.ChargerState
import com.equi.channelingoverhaul.charge.WindupState
import com.equi.channelingoverhaul.core.StationView
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModAttachments {
    val REGISTRY: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ChannelingOverhaul.ID)

    /**
     * Charger bookkeeping on a thrown trident. Not saved: a trident loaded from disk starts fresh,
     * which is how the charger notices the gap while its chunk was unloaded.
     */
    val CHARGER: AttachmentType<ChargerState> by REGISTRY.register("charger") { ->
        AttachmentType.builder { -> ChargerState() }.build()
    }

    /** Windup of a player in progress, server side. Not saved: a windup doesn't outlive a relog. */
    val WINDUP: AttachmentType<WindupState> by REGISTRY.register("windup") { ->
        AttachmentType.builder { -> WindupState() }.build()
    }

    private val STATION_CODEC: StreamCodec<ByteBuf, StationView> = StreamCodec.of(
        { buf, view ->
            listOf(view.charge, view.qMax, view.radius, view.inflow, view.antenna).forEach { buf.writeFloat(it.toFloat()) }
        },
        { buf ->
            StationView(buf.readFloat().toDouble(), buf.readFloat().toDouble(), buf.readFloat().toDouble(), buf.readFloat().toDouble(), buf.readFloat().toDouble())
        },
    )

    /**
     * Only to clients that can take the payload: a client without the mod's channels (a test connection
     * among them) would make NeoForge throw instead.
     */
    private fun canReceive(holder: IAttachmentHolder, player: ServerPlayer) = player.connection.hasChannel(SyncAttachmentsPayload.TYPE)

    /**
     * What the client knows of a stuck trident with Channeling: charge, Qmax, radius, inflow and antenna.
     * Set by `StationSync` only on a noticeable change, removed when the trident leaves the block. Not saved.
     */
    val STATION: AttachmentType<StationView> by REGISTRY.register("station") { ->
        AttachmentType.builder { -> StationView(0.0, 0.0, 0.0, 0.0, 1.0) }.sync(::canReceive, STATION_CODEC).build()
    }

    /** The windup of a player is adding charge right now: everyone around sees sparks at the trident. */
    val WINDUP_SPARKS: AttachmentType<Boolean> by REGISTRY.register("windup_sparks") { ->
        AttachmentType.builder { -> false }.sync(::canReceive, ByteBufCodecs.BOOL).build()
    }
}
