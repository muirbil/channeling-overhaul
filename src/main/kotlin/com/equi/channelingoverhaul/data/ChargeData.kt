package com.equi.channelingoverhaul.data

import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Charge stored in a trident stack. [charge] is the value at [lastGameTime];
 * the leak since then is applied lazily on read, so stacks never need to tick.
 */
data class ChargeData(val charge: Float, val lastGameTime: Long) {
    /** Charge at [gameTime] after the leak since [lastGameTime]. */
    fun chargeAt(gameTime: Long, p: ChargeParams): Double {
        val elapsed = (gameTime - lastGameTime).coerceAtLeast(0L)
        return ChargeMath.leak(charge.toDouble(), ChargeMath.ticksToSeconds(elapsed), p)
    }

    companion object {
        val CODEC: Codec<ChargeData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("charge").forGetter(ChargeData::charge),
                Codec.LONG.fieldOf("last_game_time").forGetter(ChargeData::lastGameTime),
            ).apply(instance) { charge, lastGameTime -> ChargeData(charge, lastGameTime) }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, ChargeData> = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ChargeData::charge,
            ByteBufCodecs.VAR_LONG, ChargeData::lastGameTime,
        ) { charge, lastGameTime -> ChargeData(charge, lastGameTime) }
    }
}
