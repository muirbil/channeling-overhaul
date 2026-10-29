package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.ChannelingOverhaul
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.FunctionGameTestInstance
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.TestData
import net.minecraft.gametest.framework.TestEnvironmentDefinition
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Rotation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.GameTestHooks
import net.neoforged.neoforge.registries.RegisterEvent
import java.util.function.Consumer

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
    /** The weather of [RAIN] stays for the batches after it, so tests that depend on it set their own. */
    private enum class Environment { DEFAULT, RAIN, CLEAR }

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
    )

    private fun id(path: String) = Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, path)

    @SubscribeEvent
    fun onRegister(event: RegisterEvent) {
        if (!GameTestHooks.isGametestEnabled()) return
        event.register(Registries.TEST_FUNCTION) { helper ->
            for (test in TESTS) helper.register(id(test.name), Consumer<GameTestHelper> { test.function(it) })
        }
    }

    @SubscribeEvent
    fun onRegisterTests(event: RegisterGameTestsEvent) {
        if (!GameTestHooks.isGametestEnabled()) return
        val environments: Map<Environment, Holder<TestEnvironmentDefinition<*>>> = mapOf(
            Environment.DEFAULT to event.registerEnvironment(id("default")),
            Environment.RAIN to event.registerEnvironment(
                id("rain"),
                TestEnvironmentDefinition.Weather(TestEnvironmentDefinition.Weather.Type.RAIN),
            ),
            Environment.CLEAR to event.registerEnvironment(
                id("clear"),
                TestEnvironmentDefinition.Weather(TestEnvironmentDefinition.Weather.Type.CLEAR),
            ),
        )
        for (test in TESTS) {
            val data = TestData(
                environments.getValue(test.environment), id(test.structure), test.maxTicks, 0, true,
                Rotation.NONE, false, 1, 1, test.skyAccess, 0,
            )
            val function = ResourceKey.create(Registries.TEST_FUNCTION, id(test.name))
            event.registerTest(id(test.name), FunctionGameTestInstance(function, data))
        }
    }
}
