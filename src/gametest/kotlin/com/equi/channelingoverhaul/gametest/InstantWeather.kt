package com.equi.channelingoverhaul.gametest

import com.mojang.serialization.MapCodec
import net.minecraft.gametest.framework.TestEnvironmentDefinition
import net.minecraft.server.level.ServerLevel

/**
 * Vanilla [TestEnvironmentDefinition.Weather] that also sets the rain and thunder levels at once. Vanilla only sets the
 * weather, and the levels then move by 0.01 a tick: a clear batch right after a rainy one still has `isRaining`
 * (rain level above 0.2) for some 80 ticks, so tridents there charge at the rain inflow and targets are wet.
 * The batch order is random, so tests that depend on the weather failed only now and then.
 */
class InstantWeather(val weather: TestEnvironmentDefinition.Weather.Type) :
    TestEnvironmentDefinition<TestEnvironmentDefinition.Weather.Type> {
    private val vanilla = TestEnvironmentDefinition.Weather(weather)

    override fun setup(level: ServerLevel): TestEnvironmentDefinition.Weather.Type {
        val previous = vanilla.setup(level)
        level.setRainLevel(if (weather == TestEnvironmentDefinition.Weather.Type.CLEAR) 0f else 1f)
        level.setThunderLevel(if (weather == TestEnvironmentDefinition.Weather.Type.THUNDER) 1f else 0f)
        return previous
    }

    override fun teardown(level: ServerLevel, saveData: TestEnvironmentDefinition.Weather.Type) = vanilla.teardown(level, saveData)

    override fun codec(): MapCodec<InstantWeather> = CODEC

    companion object {
        val CODEC: MapCodec<InstantWeather> =
            TestEnvironmentDefinition.Weather.Type.CODEC.fieldOf("weather").xmap(::InstantWeather, InstantWeather::weather)
    }
}
