package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.ChannelingOverhaul
import com.equi.channelingoverhaul.charge.isStuck
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.gametest.DischargeGameTests.stone
import com.equi.channelingoverhaul.gametest.DischargeGameTests.zombie
import com.equi.channelingoverhaul.registry.ModTriggers
import net.minecraft.advancements.criterion.InventoryChangeTrigger
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Advancements of the mod in a real world: they load with our triggers, each trigger fires for the owner on the real
 * action, and the debug command and `/damage` with our damage types give nothing. Channeling I: Qmax = 16.
 */
object AdvancementGameTests {
    private const val QMAX = 16.0
    private val TREE = mapOf(
        "root" to null, "fully_charged" to "root", "lightning_catcher" to "fully_charged",
        "lord_of_storms" to "lightning_catcher", "chain_reaction" to "root", "bath_time" to "chain_reaction",
        "eight_links" to "chain_reaction", "hundred" to "eight_links",
    )
    private val CHARGE_ADVANCEMENTS = setOf("fully_charged", "lightning_catcher")
    private val DISCHARGE_ADVANCEMENTS = setOf("chain_reaction", "bath_time", "eight_links", "hundred")

    /** All eight load with their parents, the copper background on the root and our triggers on the six that need them. */
    fun advancementsLoad(helper: GameTestHelper) {
        val manager = helper.level.server.advancements
        for ((name, parent) in TREE) {
            val holder = manager.get(id(name)) ?: return helper.fail("advancement $name didn't load")
            val advancement = holder.value()
            helper.assertTrue(advancement.parent().orElse(null) == parent?.let(::id), "$name: parent ${advancement.parent()}")
            val background = advancement.display().get().background.map { it.texturePath().toString() }.orElse(null)
            val expected = if (parent == null) "minecraft:textures/block/copper_block.png" else null
            helper.assertTrue(background == expected, "$name: background $background")
            val trigger = advancement.criteria().values.single().trigger()
            val want = when (name) {
                in CHARGE_ADVANCEMENTS -> ModTriggers.CHARGE.get()
                in DISCHARGE_ADVANCEMENTS -> ModTriggers.DISCHARGE.get()
                else -> null
            }
            helper.assertTrue(want == null || trigger === want, "$name: trigger $trigger")
        }
        helper.succeed()
    }

    /** The item predicates of Storm Tamer and Lord of Storms, as the game decoded them, on real stacks. */
    fun tridentPredicates(helper: GameTestHelper) {
        fun items(name: String) =
            (helper.level.server.advancements.get(id(name))!!.value().criteria().values.single().triggerInstance()
                as InventoryChangeTrigger.TriggerInstance).items().single()
        val root = items("root")
        val lord = items("lord_of_storms")
        val book = ItemStack(Items.ENCHANTED_BOOK)
        book.enchant(channeling(helper), 31)
        val cases = listOf(
            "plain trident" to ItemStack(Items.TRIDENT), "Channeling I" to trident(helper, 1),
            "Channeling XXX" to trident(helper, 30), "Channeling XXXI" to trident(helper, 31), "book" to book,
        )
        val expected = mapOf(
            "plain trident" to (false to false), "Channeling I" to (true to false),
            "Channeling XXX" to (true to false), "Channeling XXXI" to (true to true), "book" to (false to false),
        )
        for ((name, stack) in cases) {
            val actual = root.test(stack) to lord.test(stack)
            helper.assertTrue(actual == expected[name], "$name: root and lord match $actual")
        }
        helper.succeed()
    }

    /** A Channeling trident in the inventory gives Storm Tamer, a Channeling XXXI one Lord of Storms. */
    fun stormTamerFromInventory(helper: GameTestHelper) {
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(4.5, 1.0, 4.5))
        player.initInventoryMenu()
        fun pickUp(stack: ItemStack) {
            player.inventory.add(stack)
            player.containerMenu.broadcastChanges()
        }
        pickUp(ItemStack(Items.TRIDENT))
        helper.assertTrue(!done(helper, player, "root"), "Storm Tamer for a plain trident")
        pickUp(trident(helper, 1))
        helper.assertTrue(done(helper, player, "root") && !done(helper, player, "lord_of_storms"), "after Channeling I")
        pickUp(trident(helper, 31))
        helper.assertTrue(done(helper, player, "lord_of_storms"), "no Lord of Storms for Channeling XXXI")
        player.discard()
        helper.succeed()
    }

    /** In the rain, a stuck trident just under the full mark crosses it charging from the sky: Fully Charged. */
    fun fullyChargedFromSky(helper: GameTestHelper) {
        StationGameTests.stoneFloor(helper)
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(1.5, 1.0, 1.5))
        val trident = StationGameTests.dropTrident(helper, 0.995 * QMAX - 0.0005, player)
        helper.onEachTick {
            if (!done(helper, player, "fully_charged")) return@onEachTick
            val charge = TridentCharge.stored(trident.pickupItemStackOrigin)
            helper.assertTrue(trident.isStuck && charge >= 0.995 * QMAX, "Fully Charged at charge $charge")
            helper.assertTrue(!done(helper, player, "lightning_catcher"), "Lightning Catcher without lightning")
            trident.discard()
            player.discard()
            helper.succeed()
        }
    }

    /** A bolt into a rod next to a stuck trident gives Lightning Catcher; 2 + 8 of 16 is not full yet. */
    fun lightningCatcher(helper: GameTestHelper) {
        StationGameTests.stoneFloor(helper)
        helper.setBlock(6, 1, 4, Blocks.LIGHTNING_ROD)
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(1.5, 1.0, 1.5))
        var trident: ThrownTrident? = null
        helper.runAfterDelay(10) { trident = StationGameTests.dropTrident(helper, 2.0, player) }
        helper.runAfterDelay(40) {
            val stuck = trident ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(stuck.isStuck, "the trident is not stuck")
            helper.assertTrue(!done(helper, player, "lightning_catcher"), "Lightning Catcher before the bolt")
            val bolt = EntityType.LIGHTNING_BOLT.create(helper.level, EntitySpawnReason.TRIGGERED)!!
            bolt.snapTo(helper.absoluteVec(Vec3(6.5, 2.0, 4.5)))
            helper.level.addFreshEntity(bolt)
            helper.assertTrue(done(helper, player, "lightning_catcher"), "no Lightning Catcher after a bolt into the rod")
            helper.assertTrue(!done(helper, player, "fully_charged"), "Fully Charged at ${TridentCharge.stored(stuck.pickupItemStackOrigin)}")
            stuck.discard()
            player.discard()
            helper.succeed()
        }
    }

    /**
     * `/channeling charge fill` fills the held trident without Fully Charged, and the filled trident charging in the rain
     * afterwards doesn't cross the full mark either. `/channeling station` only reads.
     */
    fun chargeCommandGivesNothing(helper: GameTestHelper) {
        StationGameTests.stoneFloor(helper)
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(1.5, 1.0, 1.5))
        player.setItemInHand(InteractionHand.MAIN_HAND, trident(helper, 1))
        command(helper, player, "channeling charge fill")
        val filled = TridentCharge.stored(player.mainHandItem)
        helper.assertTrue(filled == QMAX, "the command filled the trident to $filled")
        helper.assertTrue(!done(helper, player, "fully_charged"), "Fully Charged from the command")

        val trident = ThrownTrident(helper.level, player, player.mainHandItem.copy())
        trident.setPos(helper.absoluteVec(Vec3(4.5, 2.0, 4.5)))
        trident.deltaMovement = Vec3(0.0, -1.0, 0.0)
        helper.level.addFreshEntity(trident)
        helper.runAfterDelay(60) {
            helper.assertTrue(trident.isStuck, "the trident is not stuck")
            command(helper, player, "channeling station")
            helper.assertTrue(CHARGE_ADVANCEMENTS.none { done(helper, player, it) }, "a charge advancement after the command")
            trident.discard()
            player.discard()
            helper.succeed()
        }
    }

    /**
     * Channeling XXXI with Q = 180 into the first of two rows of five zombies: the primary target takes D ≈ 108 and
     * the chain makes all 8 hops along the rows. Chain Reaction, Eight Links and Hundred; no water, so no Bath Time.
     */
    fun dischargeAlongChain(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        for (z in listOf(3, 6)) for (x in 0..8 step 2) zombie(helper, BlockPos(x, 1, z))
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(4.5, 1.0, 8.5))
        helper.runAfterDelay(10) {
            DischargeGameTests.throwAt(helper, player, 180.0, Vec3(0.5, 3.6, 3.5), Vec3(0.0, -1.0, 0.0), channeling = 31)
        }
        helper.runAfterDelay(60) {
            for (name in listOf("chain_reaction", "eight_links", "hundred")) helper.assertTrue(done(helper, player, name), "no $name")
            helper.assertTrue(!done(helper, player, "bath_time"), "Bath Time without water")
            player.discard()
            helper.succeed()
        }
    }

    /** Channeling I into a zombie in a pool kills the five weak zombies around it through the water: Bath Time. */
    fun dischargeThroughWater(helper: GameTestHelper) {
        pool(helper)
        zombie(helper, BlockPos(4, 1, 4))
        swimmers(helper)
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(4.5, 1.0, 8.5))
        helper.runAfterDelay(10) {
            DischargeGameTests.throwAt(helper, player, 15.0, Vec3(4.5, 3.6, 4.5), Vec3(0.0, -1.0, 0.0))
        }
        helper.runAfterDelay(40) {
            helper.assertTrue(done(helper, player, "bath_time") && done(helper, player, "chain_reaction"), "no Bath Time or Chain Reaction")
            helper.assertTrue(!done(helper, player, "eight_links") && !done(helper, player, "hundred"), "a challenge from Channeling I")
            player.discard()
            helper.succeed()
        }
    }

    /** `/damage` with `discharge_water` by the player kills the same five swimmers, but it's no discharge. */
    fun damageCommandGivesNothing(helper: GameTestHelper) {
        pool(helper)
        val swimmers = swimmers(helper)
        val player = ChainGameTests.mockServerPlayer(helper, Vec3(4.5, 1.0, 8.5))
        player.setItemInHand(InteractionHand.MAIN_HAND, trident(helper, 31))
        command(helper, player, "channeling charge fill")
        for (zombie in swimmers) {
            command(helper, player, "damage ${zombie.stringUUID} 5 ${ChannelingOverhaul.ID}:discharge_water by ${player.stringUUID}")
        }
        helper.assertTrue(swimmers.all { it.isDeadOrDying }, "a swimmer survived /damage")
        helper.assertTrue(DISCHARGE_ADVANCEMENTS.none { done(helper, player, it) }, "a discharge advancement from commands")
        player.discard()
        helper.succeed()
    }

    private fun id(path: String) = Identifier.fromNamespaceAndPath(ChannelingOverhaul.ID, path)

    private fun done(helper: GameTestHelper, player: ServerPlayer, name: String): Boolean {
        val holder = helper.level.server.advancements.get(id(name)) ?: return false.also { helper.fail("no advancement $name") }
        return player.advancements.getOrStartProgress(holder).isDone
    }

    /** Runs [command] as [player] with the permissions of the server console. */
    private fun command(helper: GameTestHelper, player: ServerPlayer, command: String) {
        val server = helper.level.server
        val source = server.createCommandSourceStack().withEntity(player).withPosition(player.position())
            .withLevel(helper.level).withSuppressedOutput()
        server.commands.performPrefixedCommand(source, command)
    }

    private fun channeling(helper: GameTestHelper) =
        helper.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING)

    private fun trident(helper: GameTestHelper, level: Int) = ItemStack(Items.TRIDENT).apply { enchant(channeling(helper), level) }

    /** Stone floor and a pool of still water x 3..7, z 3..5 at y = 1 in a stone rim one block high. */
    private fun pool(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        for (x in 2..8) for (z in 2..6) helper.setBlock(x, 1, z, if (x in 3..7 && z in 3..5) Blocks.WATER else Blocks.STONE)
    }

    /** Five zombies with 1 health in the pool, within 2 blocks of its block (4, 4). */
    private fun swimmers(helper: GameTestHelper): List<LivingEntity> =
        listOf(BlockPos(3, 1, 3), BlockPos(5, 1, 3), BlockPos(3, 1, 5), BlockPos(5, 1, 5), BlockPos(6, 1, 4))
            .map { zombie(helper, it).apply { health = 1.0f } }
}
