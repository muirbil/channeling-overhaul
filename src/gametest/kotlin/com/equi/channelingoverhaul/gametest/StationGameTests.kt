package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.charge.StationReading
import com.equi.channelingoverhaul.charge.isStuck
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ConductorType
import com.equi.channelingoverhaul.core.Conductivity
import com.equi.channelingoverhaul.core.Precipitation
import com.equi.channelingoverhaul.core.Weather
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.mixin.AbstractArrowAccessor
import com.equi.channelingoverhaul.registry.ModAttachments
import com.equi.channelingoverhaul.registry.ModDataComponents
import com.equi.channelingoverhaul.registry.ModDataMaps
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.server.commands.FillBiomeCommand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameType
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Stuck trident charging in a real world. Each test drops a Channeling I trident (R = 4) onto the
 * center of a 9×9 stone floor, so sources of the neighbouring tests stay out of its radius.
 */
object StationGameTests {
    private const val SPAWN_TICK = 10L
    private const val CHECK_TICK = 60L
    /** `AbstractArrow.tickDespawn` removes a stuck arrow at this age. */
    private const val DESPAWN_TICKS = 1200

    /** Copper, oxidized or not, waxed or not, has data; these copper-named blocks don't. */
    private val NOT_CONDUCTORS = setOf("copper_ore", "deepslate_copper_ore", "raw_copper_block", "copper_torch", "copper_wall_torch")

    fun conductivityCoversCopper(helper: GameTestHelper) {
        val wrong = mutableListOf<String>()
        var checked = 0
        for (block in BuiltInRegistries.BLOCK) {
            val id = BuiltInRegistries.BLOCK.getKey(block)
            if (id.namespace != "minecraft" || ("copper" !in id.path && "lightning_rod" !in id.path)) continue
            checked++
            val path = id.path
            val expected = when {
                path in NOT_CONDUCTORS -> null
                "lightning_rod" in path -> Conductivity(ConductorType.LIGHTNING_ROD)
                "oxidized" in path -> Conductivity(ConductorType.COPPER, 0.25)
                "weathered" in path -> Conductivity(ConductorType.COPPER, 0.5)
                "exposed" in path -> Conductivity(ConductorType.COPPER, 0.75)
                else -> Conductivity(ConductorType.COPPER, 1.0)
            }
            val actual = block.defaultBlockState().typeHolder().getData(ModDataMaps.CONDUCTIVITY)
            if (actual != expected) wrong += "$path: $actual, expected $expected"
        }
        val water = Blocks.WATER.defaultBlockState().typeHolder().getData(ModDataMaps.CONDUCTIVITY)
        helper.assertTrue(water?.type == ConductorType.WATER, "water: $water")
        // 14 copper families x 4 stages x waxed + 8 lightning rods + 5 others
        helper.assertTrue(checked == 125, "checked $checked copper blocks")
        helper.assertTrue(wrong.isEmpty(), wrong.joinToString("; "))
        helper.succeed()
    }

    /** Waxed copper keeps its stage; rods count without oxidation; source water counts. */
    fun waxedCopper(helper: GameTestHelper) {
        fillBiome(helper, Biomes.PLAINS)
        stoneFloor(helper)
        helper.setBlock(3, 0, 4, Blocks.WAXED_COPPER_BLOCK)
        helper.setBlock(5, 0, 4, Blocks.WAXED_EXPOSED_COPPER)
        helper.setBlock(4, 0, 3, Blocks.WAXED_WEATHERED_COPPER)
        helper.setBlock(4, 0, 5, Blocks.WAXED_OXIDIZED_COPPER)
        helper.setBlock(6, 1, 6, Blocks.WAXED_OXIDIZED_LIGHTNING_ROD)
        helper.setBlock(2, 0, 2, Blocks.WATER)
        withStuckTrident(helper, charge = 0.0) { trident ->
            val reading = reading(trident)
            val conductivities = reading.scan.copper.map { it.conductivity }.sorted()
            helper.assertTrue(conductivities == listOf(0.25, 0.5, 0.75, 1.0), "copper: $conductivities")
            helper.assertTrue(reading.scan.rods.size == 1, "rods: ${reading.scan.rods}")
            helper.assertTrue(reading.scan.water.size == 1, "water: ${reading.scan.water}")
            helper.assertTrue(reading.canSeeSky && reading.weather == Weather.RAIN, "sky: $reading")
            helper.assertTrue(reading.precipitation == Precipitation.RAIN, "precipitation: ${reading.precipitation}")
            val charge = TridentCharge.stored(trident.pickupItemStackOrigin)
            helper.assertTrue(charge > 0.0 && charge <= ChargeMath.capacity(1, ModConfig.params), "charge: $charge")
        }
    }

    /** No open sky: no inflow and no leak, but every step still moves the timestamp. */
    fun underRoof(helper: GameTestHelper) {
        stoneFloor(helper)
        for (x in 0..8) for (z in 0..8) helper.setBlock(x, 3, z, Blocks.STONE)
        // Sky light under the new roof is recomputed asynchronously, under load it can take a while
        val roofed = { !helper.level.canSeeSky(helper.absolutePos(BlockPos(4, 1, 4))) }
        withStuckTrident(helper, charge = 10.0, ready = roofed) { trident ->
            val reading = reading(trident)
            helper.assertTrue(!reading.canSeeSky && reading.sky == 0.0, "sky: $reading")
            val data = trident.pickupItemStackOrigin.get(ModDataComponents.CHARGE)
            // 50 ticks of leak would leave 9.89; the flight before sticking costs 0.005 at most
            helper.assertTrue(data != null && data.charge in 9.99f..10f, "charge: $data")
            val age = helper.level.gameTime - (data?.lastGameTime ?: 0L)
            helper.assertTrue(age <= ModConfig.params.chargeIntervalTicks, "timestamp is $age ticks old")
        }
    }

    /** A biome without precipitation counts as clear weather even in the rain. */
    fun dryBiome(helper: GameTestHelper) {
        fillBiome(helper, Biomes.DESERT)
        stoneFloor(helper)
        withStuckTrident(helper, charge = 0.0) { trident ->
            val reading = reading(trident)
            helper.assertTrue(reading.precipitation == Precipitation.NONE, "precipitation: ${reading.precipitation}")
            helper.assertTrue(reading.weather == Weather.RAIN && reading.canSeeSky, "sky: $reading")
            helper.assertTrue(reading.sky == ModConfig.params.skyClear, "S = ${reading.sky}")
            helper.assertTrue(TridentCharge.stored(trident.pickupItemStackOrigin) > 0.0, "no charge")
        }
    }

    /** A Loyalty trident stuck in copper stays past the vanilla 4 ticks; its owner picks it up with the charge. */
    fun loyaltyHeldByCopper(helper: GameTestHelper) {
        stoneFloor(helper)
        helper.setBlock(4, 0, 4, Blocks.COPPER_BLOCK)
        val owner = mockOwner(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(SPAWN_TICK) { trident = dropTrident(helper, charge = 10.0, thrower = owner, loyalty = 3) }
        helper.runAfterDelay(CHECK_TICK) {
            val held = trident ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(held.isStuck && !held.isNoPhysics, "the trident left the copper")
            held.playerTouch(owner)
            helper.assertTrue(held.isRemoved, "the owner can't pick the trident up")
            val inventory = owner.inventory
            val picked = (0 until inventory.containerSize).map(inventory::getItem).firstOrNull { it.`is`(Items.TRIDENT) }
            helper.assertTrue(picked != null && TridentCharge.stored(picked) >= 9.99, "picked up: $picked")
            helper.succeed()
        }
    }

    /** A lightning rod holds it too, whatever the oxidation; once the rod is gone the trident returns. */
    fun loyaltyHeldByRod(helper: GameTestHelper) {
        stoneFloor(helper)
        helper.setBlock(4, 1, 4, Blocks.OXIDIZED_LIGHTNING_ROD)
        val owner = mockOwner(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(SPAWN_TICK) { trident = dropTrident(helper, charge = 0.0, thrower = owner, loyalty = 3, y = 3.0) }
        helper.runAfterDelay(CHECK_TICK) {
            helper.assertTrue(trident?.let { it.isStuck && !it.isNoPhysics } == true, "the trident left the rod")
            helper.setBlock(4, 1, 4, Blocks.AIR)
        }
        helper.runAfterDelay(CHECK_TICK + 10) {
            helper.assertTrue(trident?.isNoPhysics == true, "the trident doesn't return after the rod is gone")
            helper.succeed()
        }
    }

    /** Anywhere else a Loyalty trident returns as in vanilla, and doesn't charge on the way back. */
    fun loyaltyReturnsFromStone(helper: GameTestHelper) {
        stoneFloor(helper)
        val owner = mockOwner(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(SPAWN_TICK) { trident = dropTrident(helper, charge = 10.0, thrower = owner, loyalty = 3) }
        helper.runAfterDelay(CHECK_TICK) {
            val returning = trident ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(returning.isNoPhysics, "the trident doesn't return")
            val charge = TridentCharge.stored(returning.pickupItemStackOrigin)
            helper.assertTrue(charge <= 10.0, "charged on the way back: $charge")
            helper.assertTrue(!returning.isStuck, "a returning trident counts as stuck")
            helper.succeed()
        }
    }

    /** Without Channeling there is nothing to charge: copper doesn't hold the trident, it returns as in vanilla. */
    fun loyaltyWithoutChanneling(helper: GameTestHelper) {
        stoneFloor(helper)
        helper.setBlock(4, 0, 4, Blocks.COPPER_BLOCK)
        val owner = mockOwner(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(SPAWN_TICK) { trident = dropTrident(helper, charge = 0.0, thrower = owner, loyalty = 3, channeling = 0) }
        helper.runAfterDelay(CHECK_TICK) {
            helper.assertTrue(trident?.isNoPhysics == true, "copper holds a trident without Channeling")
            helper.succeed()
        }
    }

    /** Thrown by a player, with Channeling: stays stuck past the vanilla despawn, like a Loyalty trident. */
    fun channelingStaysStuck(helper: GameTestHelper) =
        despawnCase(helper, thrower = mockOwner(helper), channeling = 1, stays = true)

    /** Thrown by a player, without Channeling: vanilla despawn. */
    fun plainDespawns(helper: GameTestHelper) =
        despawnCase(helper, thrower = mockOwner(helper), channeling = 0, stays = false)

    /** With Channeling but not for pickup: vanilla despawn. */
    fun unpickableDespawns(helper: GameTestHelper) =
        despawnCase(helper, thrower = null, channeling = 1, stays = false)

    /** Sticks a trident, sets its despawn timer 10 ticks short of the vanilla limit and checks 20 ticks later whether it [stays]. */
    private fun despawnCase(helper: GameTestHelper, thrower: Player?, channeling: Int, stays: Boolean) {
        stoneFloor(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(SPAWN_TICK) { trident = dropTrident(helper, charge = 0.0, thrower = thrower, channeling = channeling) }
        helper.runAfterDelay(CHECK_TICK) {
            val stuck = trident ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(stuck.isStuck, "the trident is not stuck")
            (stuck as AbstractArrowAccessor).`channeling_overhaul$setLife`(DESPAWN_TICKS - 10)
        }
        helper.runAfterDelay(CHECK_TICK + 20) {
            val removed = trident?.isRemoved == true
            helper.assertTrue(removed != stays, if (stays) "the trident despawned" else "the trident didn't despawn")
            helper.succeed()
        }
    }

    /** Not in the level, so it neither ticks nor picks anything up by itself. */
    internal fun mockOwner(helper: GameTestHelper): Player =
        helper.makeMockPlayer(GameType.SURVIVAL).apply { setPos(helper.absoluteVec(Vec3(4.5, 1.0, 1.5))) }

    internal fun stoneFloor(helper: GameTestHelper) {
        for (x in 0..8) for (z in 0..8) helper.setBlock(x, 0, z, Blocks.STONE)
    }

    /** Only the middle of the area, so biome cells of the neighbouring tests stay as they are. */
    internal fun fillBiome(helper: GameTestHelper, biome: ResourceKey<Biome>) {
        val holder = helper.level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome)
        FillBiomeCommand.fill(helper.level, helper.absolutePos(BlockPos(2, 0, 2)), helper.absolutePos(BlockPos(6, 4, 6)), holder)
            .ifRight { helper.fail("fillbiome: ${it.message}") }
    }

    /**
     * Drops a Channeling I trident with [charge] at [SPAWN_TICK], or on the first tick after it when the area is [ready],
     * runs [check] CHECK_TICK − SPAWN_TICK ticks after the drop, then succeeds.
     */
    private fun withStuckTrident(
        helper: GameTestHelper, charge: Double, ready: () -> Boolean = { true }, check: (ThrownTrident) -> Unit,
    ) {
        var trident: ThrownTrident? = null
        var checkTick = Long.MAX_VALUE
        helper.onEachTick {
            if (trident == null && helper.tick >= SPAWN_TICK && ready()) {
                trident = dropTrident(helper, charge)
                checkTick = helper.tick + CHECK_TICK - SPAWN_TICK
            }
            val stuck = trident
            if (stuck != null && helper.tick == checkTick) {
                helper.assertTrue(stuck.isStuck, "the trident is not stuck")
                check(stuck)
                helper.succeed()
            }
        }
    }

    /**
     * A trident falling onto (x, z), the center by default. Thrown by a [thrower] it is theirs and can be picked up,
     * as from a player's hand; without one nobody can pick it up, as a Drowned's.
     */
    internal fun dropTrident(
        helper: GameTestHelper, charge: Double, thrower: Player? = null, y: Double = 2.0, channeling: Int = 1, loyalty: Int = 0,
        x: Double = 4.5, z: Double = 4.5,
    ): ThrownTrident {
        val level = helper.level
        val enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        val stack = ItemStack(Items.TRIDENT)
        if (channeling > 0) stack.enchant(enchantments.getOrThrow(Enchantments.CHANNELING), channeling)
        if (loyalty > 0) stack.enchant(enchantments.getOrThrow(Enchantments.LOYALTY), loyalty)
        val pos = helper.absoluteVec(Vec3(x, y, z))
        val trident =
            if (thrower == null) ThrownTrident(level, pos.x, pos.y, pos.z, stack)
            else ThrownTrident(level, thrower, stack).apply { setPos(pos) }
        trident.deltaMovement = Vec3(0.0, -1.0, 0.0)
        TridentCharge.write(trident.pickupItemStackOrigin, charge, level.gameTime)
        level.addFreshEntity(trident)
        return trident
    }

    private fun reading(trident: ThrownTrident) =
        StationReading.of(trident, 1, trident.getData(ModAttachments.CHARGER).scan, ModConfig.params)
}
