package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.ChannelingOverhaul
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTestGenerator
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.TestFunction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent

/**
 * Game tests, run with `./gradlew runGameTestServer`. They load the real data packs,
 * so they check what unit tests can't: our data merged with Apothic Enchanting's,
 * and charging in a real world.
 *
 * The `gametest` source set with these tests and their structures is part of the mod only in dev runs,
 * the release jar is built from `main` alone. So the mod object doesn't know about them: they subscribe themselves.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object ModGameTests {
    private const val RAIN_BATCH = "rain"
    private const val CLEAR_BATCH = "clear"
    private const val WEATHER_TICKS = 20_000_000

    /** The weather of [RAIN] stays for the batches after it, so tests that depend on it set their own. */
    private enum class Environment(val batch: String) { DEFAULT("default"), RAIN(RAIN_BATCH), CLEAR(CLEAR_BATCH) }

    private class Test(
        val name: String,
        val function: (GameTestHelper) -> Unit,
        val environment: Environment = Environment.DEFAULT,
        val structure: String = "empty",
        val maxTicks: Int = 20,
        val skyAccess: Boolean = false,
    )

    private val TESTS = listOf(
        Test("enchantment_data", EnchantmentGameTests::enchantmentData),
        Test("mob_hit", DependencyGameTests::mobHit, structure = "station"),
        Test("conductivity_covers_copper", StationGameTests::conductivityCoversCopper),
        Test("waxed_copper", StationGameTests::waxedCopper, Environment.RAIN, "station", 100, skyAccess = true),
        Test("under_roof", StationGameTests::underRoof, Environment.RAIN, "station", 100, skyAccess = true),
        Test("dry_biome", StationGameTests::dryBiome, Environment.RAIN, "station", 100, skyAccess = true),
        Test("loyalty_held_by_copper", StationGameTests::loyaltyHeldByCopper, structure = "station", maxTicks = 100, skyAccess = true),
        Test("loyalty_held_by_rod", StationGameTests::loyaltyHeldByRod, structure = "station", maxTicks = 100, skyAccess = true),
        Test("loyalty_returns_from_stone", StationGameTests::loyaltyReturnsFromStone, structure = "station", maxTicks = 100, skyAccess = true),
        Test("loyalty_without_channeling", StationGameTests::loyaltyWithoutChanneling, structure = "station", maxTicks = 100, skyAccess = true),
        Test("channeling_stays_stuck", StationGameTests::channelingStaysStuck, structure = "station", maxTicks = 100),
        Test("plain_despawns", StationGameTests::plainDespawns, structure = "station", maxTicks = 100),
        Test("unpickable_despawns", StationGameTests::unpickableDespawns, structure = "station", maxTicks = 100),
        Test("discharge_into_zombie", DischargeGameTests::dischargeIntoZombie, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("strong_discharge_bolt", DischargeGameTests::strongDischargeBolt, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("below_threshold_keeps_charge", DischargeGameTests::belowThresholdKeepsCharge, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("shield_keeps_charge", DischargeGameTests::shieldKeepsCharge, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("armor_and_water", DischargeGameTests::armorAndWater, Environment.CLEAR, structure = "station", maxTicks = 40),
        Test("armor_conductivity_covers_armor", DischargeGameTests::armorConductivityCoversArmor),
        Test("death_messages", DischargeGameTests::deathMessages, Environment.CLEAR, structure = "station"),
        Test("chain_along_zombies", ChainGameTests::chainAlongZombies, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("chain_through_dead_link", ChainGameTests::chainThroughDeadLink, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("chain_skips_owner_and_pet", ChainGameTests::chainSkipsOwnerAndPet, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("water_hits_only_water", ChainGameTests::waterHitsOnlyWater, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("one_hit_each", ChainGameTests::oneHitEach, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("water_same_body", ChainGameTests::waterSameBody, Environment.CLEAR, structure = "station", maxTicks = 20),
        Test("death_message_modes", ChainGameTests::deathMessageModes, Environment.CLEAR, structure = "station"),
        Test("lightning_nearby", LightningGameTests::lightningNearby, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("lightning_into_rod", LightningGameTests::lightningIntoRod, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("lightning_capped_at_qmax", LightningGameTests::lightningCappedAtQmax, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("lightning_ignored", LightningGameTests::lightningIgnored, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("lightning_held_loyalty", LightningGameTests::lightningHeldLoyalty, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("station_sync", LightningGameTests::stationSync, Environment.CLEAR, structure = "station", maxTicks = 70),
        Test("windup_into_thrown_trident", LightningGameTests::windupIntoThrownTrident, Environment.RAIN, "station", 100, skyAccess = true),
        Test("windup_stops_at_cap", LightningGameTests::windupStopsAtCap, Environment.RAIN, "station", 100, skyAccess = true),
        Test("windup_above_cap", LightningGameTests::windupAboveCap, Environment.RAIN, "station", 100, skyAccess = true),
        Test("rain_from_first_tick", ::rainFromFirstTick, Environment.RAIN),
        Test("clear_from_first_tick", ::clearFromFirstTick, Environment.CLEAR),
        Test("advancements_load", AdvancementGameTests::advancementsLoad),
        Test("advancement_trident_predicates", AdvancementGameTests::tridentPredicates),
        Test("advancement_storm_tamer", AdvancementGameTests::stormTamerFromInventory),
        Test("advancement_fully_charged", AdvancementGameTests::fullyChargedFromSky, Environment.RAIN, "station", 100, skyAccess = true),
        Test("advancement_lightning_catcher", AdvancementGameTests::lightningCatcher, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("advancement_charge_command", AdvancementGameTests::chargeCommandGivesNothing, Environment.RAIN, "station", 100, skyAccess = true),
        Test("advancement_chain", AdvancementGameTests::dischargeAlongChain, Environment.CLEAR, structure = "station", maxTicks = 80),
        Test("advancement_bath_time", AdvancementGameTests::dischargeThroughWater, Environment.CLEAR, structure = "station", maxTicks = 60),
        Test("advancement_damage_command", AdvancementGameTests::damageCommandGivesNothing, Environment.CLEAR, structure = "station"),
    )

    /** Each environment is a batch of its own; `GameTestRegistry` needs a static method on an `object`. */
    @JvmStatic
    @GameTestGenerator
    fun tests(): Collection<TestFunction> = TESTS.map { test ->
        TestFunction(
            test.environment.batch, test.name, "${ChannelingOverhaul.ID}:${test.structure}", Rotation.NONE,
            test.maxTicks, 0L, true, false, 1, 1, test.skyAccess, test.function,
        )
    }

    /**
     * The weather flags alone move the rain and thunder levels by 0.01 a tick, and `isRaining` waits for rain level 0.2:
     * a clear batch right after a rainy one would still rain for some 80 ticks. So the levels are set at once too.
     */
    @JvmStatic
    @BeforeBatch(batch = RAIN_BATCH)
    fun rain(level: ServerLevel) {
        level.setWeatherParameters(0, WEATHER_TICKS, true, false)
        level.setRainLevel(1.0f)
        level.setThunderLevel(0.0f)
    }

    @JvmStatic
    @BeforeBatch(batch = CLEAR_BATCH)
    fun clear(level: ServerLevel) {
        level.setWeatherParameters(WEATHER_TICKS, 0, false, false)
        level.setRainLevel(0.0f)
        level.setThunderLevel(0.0f)
    }

    /** Batches run in no fixed order, so these two also catch weather left over from the batch before. */
    private fun rainFromFirstTick(helper: GameTestHelper) {
        helper.assertTrue(helper.level.isRaining && !helper.level.isThundering, "no rain at tick ${helper.tick}")
        helper.succeed()
    }

    private fun clearFromFirstTick(helper: GameTestHelper) {
        helper.assertTrue(!helper.level.isRaining && !helper.level.isThundering, "rain at tick ${helper.tick}")
        helper.succeed()
    }

    @SubscribeEvent
    fun onRegisterTests(event: RegisterGameTestsEvent) = event.register(ModGameTests::class.java)
}
