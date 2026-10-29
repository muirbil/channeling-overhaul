package com.equi.channelingoverhaul.network

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.client.ClientSparks
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.HandlerThread
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Tells the clients around to draw sparks: the server knows where, the client decides how many by its own particle
 * settings. [from] and [to] are the ends of an arc; for a burst [from] is the center and [to] the half-size.
 */
data class SparksPayload(val kind: Kind, val from: Vec3, val to: Vec3) : CustomPacketPayload {
    enum class Kind {
        /** A chain hop between two links */
        ARC,
        /** A hit through water: sparks and bubbles all over the target */
        WATER,
        /** Sparks at a trident a lightning bolt charged */
        BURST,
    }

    override fun type(): CustomPacketPayload.Type<SparksPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SparksPayload>(Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, "sparks"))

        private val STREAM_CODEC: StreamCodec<ByteBuf, SparksPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeByte(payload.kind.ordinal)
                Vec3.STREAM_CODEC.encode(buf, payload.from)
                Vec3.STREAM_CODEC.encode(buf, payload.to)
            },
            { buf -> SparksPayload(Kind.entries[buf.readByte().toInt()], Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf)) },
        )

        /** Players this far from the sparks get them. */
        private const val RANGE = 48.0

        fun register() {
            MOD_BUS.addListener(RegisterPayloadHandlersEvent::class.java) { event ->
                event.registrar("1").executesOn(HandlerThread.MAIN).optional()
                    .playToClient(TYPE, STREAM_CODEC) { payload: SparksPayload, _: IPayloadContext -> ClientSparks.play(payload) }
            }
        }

        /** To the players near [at] that have the channel; others, a vanilla client or a test connection, go without. */
        fun send(level: ServerLevel, at: Vec3, payload: SparksPayload) {
            for (player in level.players()) {
                if (player.distanceToSqr(at) <= RANGE * RANGE && player.connection.hasChannel(TYPE)) player.connection.send(payload)
            }
        }
    }
}
