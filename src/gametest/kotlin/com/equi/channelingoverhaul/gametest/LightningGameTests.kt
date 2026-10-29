package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.charge.isStuck
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.core.ChargeParams
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.core.StationView
import com.equi.channelingoverhaul.registry.ModAttachments
import com.equi.channelingoverhaul.registry.ModDataComponents
import com.equi.channelingoverhaul.registry.ModEntities
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Stage 7: lightning near stuck Channeling I tridents (R = 4, Qmax = 16) and windup. The charge is read right before
 * and right after the bolt joins the world, in the same tick, so the slow charging from the sky doesn't blur it.
 */
object LightningGameTests {
    private const val DROP_TICK = 10L
    private const val STRIKE_TICK = 40L
    private const val QMAX = 16.0

    /** A bolt 2 and 2.8 blocks from two stuck tridents: one bolt gives each of them +25% Qmax. */
    fun lightningNearby(helper: GameTestHelper) = strikes(
        helper, at = listOf(4.5 to 4.5, 4.5 to 6.5), charge = 2.0,
        bolts = { bolt(helper, Vec3(6.5, 1.0, 4.5)) },
    ) { before, after -> abs(after - before - 0.25 * QMAX) < 1e-4 }

    /** A bolt on top of a lightning rod strikes the rod: +50% Qmax. */
    fun lightningIntoRod(helper: GameTestHelper) = strikes(
        helper, at = listOf(4.5 to 4.5), charge = 2.0,
        setup = { helper.setBlock(6, 1, 4, Blocks.LIGHTNING_ROD) },
        bolts = { bolt(helper, Vec3(6.5, 2.0, 4.5)) },
    ) { before, after -> abs(after - before - 0.5 * QMAX) < 1e-4 }

    /** A rod strike into an almost full trident stops at Qmax. */
    fun lightningCappedAtQmax(helper: GameTestHelper) = strikes(
        helper, at = listOf(4.5 to 4.5), charge = 14.0,
        setup = { helper.setBlock(6, 1, 4, Blocks.LIGHTNING_ROD) },
        bolts = { bolt(helper, Vec3(6.5, 2.0, 4.5)) },
    ) { _, after -> after == QMAX }

    /** Nothing from a real bolt 5.7 blocks away, a visual-only bolt next to it, or our own discharge bolt. */
    fun lightningIgnored(helper: GameTestHelper) = strikes(
        helper, at = listOf(4.5 to 4.5), charge = 2.0,
        bolts = {
            bolt(helper, Vec3(8.5, 1.0, 8.5))
            bolt(helper, Vec3(6.5, 1.0, 4.5), visualOnly = true)
            spawn(helper, ModEntities.DISCHARGE_BOLT.create(helper.level, EntitySpawnReason.TRIGGERED)!!, Vec3(5.5, 1.0, 4.5))
        },
    ) { before, after -> after == before }

    /** A Loyalty trident held in copper is a stuck trident too. */
    fun lightningHeldLoyalty(helper: GameTestHelper) = strikes(
        helper, at = listOf(4.5 to 4.5), charge = 2.0, thrower = StationGameTests.mockOwner(helper), loyalty = 3,
        setup = { helper.setBlock(4, 0, 4, Blocks.COPPER_BLOCK) },
        bolts = { bolt(helper, Vec3(6.5, 1.0, 4.5)) },
    ) { before, after -> abs(after - before - 0.25 * QMAX) < 1e-4 }

    /**
     * The client's view of a stuck trident: set when it sticks, not sent again for a small charging step,
     * sent again after a lightning strike, removed once the trident leaves the block.
     */
    fun stationSync(helper: GameTestHelper) {
        StationGameTests.stoneFloor(helper)
        var trident: ThrownTrident? = null
        var first: StationView? = null
        var stored = 0.0
        var loosened = false
        helper.runAfterDelay(DROP_TICK) { trident = StationGameTests.dropTrident(helper, 2.0) }
        helper.runAfterDelay(30) {
            val stuck = trident ?: return@runAfterDelay helper.fail("no trident")
            val view = stuck.getExistingDataOrNull(ModAttachments.STATION) ?: return@runAfterDelay helper.fail("no view of a stuck trident")
            stored = TridentCharge.stored(stuck.pickupItemStackOrigin)
            helper.assertTrue(view.qMax == QMAX && view.radius == 4.0, "view: $view")
            helper.assertTrue(abs(view.charge - stored) < 0.01 * QMAX && view.inflow > 0.0, "view $view, stored $stored")
            first = view
        }
        helper.runAfterDelay(41) {
            val stuck = trident!!
            val now = TridentCharge.stored(stuck.pickupItemStackOrigin)
            helper.assertTrue(now > stored, "the trident didn't charge: $stored -> $now")
            helper.assertTrue(stuck.getExistingDataOrNull(ModAttachments.STATION) === first, "synced again for a step of ${now - stored}")
            bolt(helper, Vec3(6.5, 1.0, 4.5))
            val view = stuck.getExistingDataOrNull(ModAttachments.STATION)
            helper.assertTrue(view != null && abs(view.charge - TridentCharge.stored(stuck.pickupItemStackOrigin)) < 1e-4, "after the strike: $view")
            // Knocked loose: nothing left to draw while it falls (it sticks again a block lower)
            helper.setBlock(4, 0, 4, Blocks.AIR)
            loosened = true
        }
        helper.onEachTick {
            val falling = trident ?: return@onEachTick
            if (!loosened || falling.isStuck) return@onEachTick
            helper.assertTrue(!falling.hasData(ModAttachments.STATION), "the view outlived the stuck trident")
            helper.succeed()
        }
    }

    /** In the rain (S = 0.5, A = 1), 40 ticks of windup from 2.0 charge at half the inflow, and the thrown trident has it. */
    fun windupIntoThrownTrident(helper: GameTestHelper) =
        windup(helper, start = 2.0) { p -> ChargeMath.windup(2.0, p.skyRain, 2.0, QMAX, p) }

    /** From just under 30% of Qmax the windup stops at the cap. */
    fun windupStopsAtCap(helper: GameTestHelper) = windup(helper, start = 4.7) { p -> p.windupCap * QMAX }

    /** Above the cap the windup adds nothing. */
    fun windupAboveCap(helper: GameTestHelper) = windup(helper, start = 6.0) { 6.0 }

    /**
     * Drops Channeling I tridents with [charge] at [at] (x, z), waits until they are stuck, makes the [bolts]
     * and checks every trident's charge [before and after][check] them.
     */
    private fun strikes(
        helper: GameTestHelper, at: List<Pair<Double, Double>>, charge: Double, thrower: Player? = null, loyalty: Int = 0,
        setup: () -> Unit = {}, bolts: () -> Unit, check: (Double, Double) -> Boolean,
    ) {
        StationGameTests.stoneFloor(helper)
        setup()
        val tridents = mutableListOf<ThrownTrident>()
        helper.runAfterDelay(DROP_TICK) {
            at.forEach { (x, z) -> tridents += StationGameTests.dropTrident(helper, charge, thrower, loyalty = loyalty, x = x, z = z) }
        }
        helper.runAfterDelay(STRIKE_TICK) {
            helper.assertTrue(tridents.all { it.isStuck }, "not all tridents are stuck")
            val before = tridents.map { TridentCharge.stored(it.pickupItemStackOrigin) }
            bolts()
            val after = tridents.map { TridentCharge.stored(it.pickupItemStackOrigin) }
            helper.assertTrue(before.zip(after).all { (b, a) -> check(b, a) }, "charge $before -> $after")
            helper.succeed()
        }
    }

    private fun bolt(helper: GameTestHelper, at: Vec3, visualOnly: Boolean = false) {
        val bolt = EntityType.LIGHTNING_BOLT.create(helper.level, EntitySpawnReason.TRIGGERED)!!
        bolt.setVisualOnly(visualOnly)
        spawn(helper, bolt, at)
    }

    private fun spawn(helper: GameTestHelper, entity: Entity, at: Vec3) {
        entity.snapTo(helper.absoluteVec(at))
        helper.level.addFreshEntity(entity)
    }

    /**
     * A player in plains winds up a Channeling I trident with [start] charge for 40 ticks and throws it:
     * the held stack doesn't change while winding up, the thrown trident carries the [expected] charge.
     */
    private fun windup(helper: GameTestHelper, start: Double, expected: (ChargeParams) -> Double) {
        StationGameTests.stoneFloor(helper)
        StationGameTests.fillBiome(helper, Biomes.PLAINS)
        var started = false
        // The rain of the environment fades in: isRaining needs rain level 0.2, some 20 ticks after the batch starts
        helper.onEachTick {
            val level = helper.level
            if (started || !level.isRaining) return@onEachTick
            started = true
            val player = ChainGameTests.mockServerPlayer(helper, Vec3(4.5, 1.0, 4.5))
            val stack = ItemStack(Items.TRIDENT)
            stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING), 1)
            TridentCharge.write(stack, start, level.gameTime)
            player.setItemInHand(InteractionHand.MAIN_HAND, stack)
            player.startUsingItem(InteractionHand.MAIN_HAND)
            val written = stack.get(ModDataComponents.CHARGE)
            repeat(40) { player.doTick() }
            helper.assertTrue(player.isUsingItem, "the player stopped winding up")
            val held = player.mainHandItem.get(ModDataComponents.CHARGE)
            helper.assertTrue(held == written, "the held stack changed during the windup: $written -> $held")

            player.releaseUsingItem()
            val thrown = level.getEntitiesOfClass(ThrownTrident::class.java, player.boundingBox.inflate(4.0)) { it.owner === player }
            val trident = thrown.singleOrNull() ?: return@onEachTick helper.fail("thrown tridents: ${thrown.size}")
            val actual = TridentCharge.stored(trident.pickupItemStackOrigin)
            val want = expected(ModConfig.params)
            helper.assertTrue(abs(actual - want) < 1e-3, "thrown trident charge $actual, expected $want")
            trident.discard()
            player.discard()
            helper.succeed()
        }
    }
}
