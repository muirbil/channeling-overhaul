package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.config.DeathMessageMode
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.discharge.DeathAnnouncer
import com.equi.channelingoverhaul.discharge.DischargeOrigin
import com.equi.channelingoverhaul.discharge.DischargeTargets
import com.equi.channelingoverhaul.discharge.WaterSpread
import com.equi.channelingoverhaul.gametest.DischargeGameTests.assertLost
import com.equi.channelingoverhaul.gametest.DischargeGameTests.stone
import com.equi.channelingoverhaul.gametest.DischargeGameTests.zombie
import com.equi.channelingoverhaul.registry.ModDamageTypes
import com.mojang.authlib.GameProfile
import dev.shadowsoffire.apothic_attributes.api.ALObjects
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Chain and water of stage 6 with real tridents and zombies without armor points (see [DischargeGameTests]),
 * so the health each one loses is exactly its hit. Q = 15 throughout: D = 9 at M = 1.
 */
object ChainGameTests {
    private const val THROW_TICK = 10L
    private const val CHECK_TICK = 40L
    private const val CHARGE = 15.0
    private const val TOLERANCE = 0.02

    /** Channeling IX gives N = 3: a row of five zombies 2 blocks apart, each hop 0.6 of the previous, the fifth is spared. */
    fun chainAlongZombies(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        val row = (0..8 step 2).map { zombie(helper, BlockPos(it, 1, 4)) }
        helper.runAfterDelay(THROW_TICK) { throwDown(helper, DischargeGameTests.mockOwner(helper), x = 0, channeling = 9) }
        helper.runAfterDelay(CHECK_TICK) {
            val p = ModConfig.params
            helper.assertTrue(ChargeMath.chainJumps(9, p) == 3, "N(9) = ${ChargeMath.chainJumps(9, p)}")
            val expected = listOf(8.0 + ChargeMath.dischargeDamage(CHARGE, 1.0, p)) +
                (1..3).map { ChargeMath.chainBase(CHARGE, it, p) } + 0.0
            row.zip(expected).forEach { (zombie, lost) -> assertLost(helper, zombie, lost, TOLERANCE) }
            helper.succeed()
        }
    }

    /** The first link has 1 health and dies of its hop: the chain goes on from where it fell. */
    fun chainThroughDeadLink(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        val row = (0..6 step 2).map { zombie(helper, BlockPos(it, 1, 4)) }
        row[1].health = 1.0f
        helper.runAfterDelay(THROW_TICK) { throwDown(helper, DischargeGameTests.mockOwner(helper), x = 0, channeling = 9) }
        helper.runAfterDelay(CHECK_TICK) {
            val p = ModConfig.params
            helper.assertTrue(row[1].isDeadOrDying, "the first link survived")
            assertLost(helper, row[2], ChargeMath.chainBase(CHARGE, 2, p), TOLERANCE)
            assertLost(helper, row[3], ChargeMath.chainBase(CHARGE, 3, p), TOLERANCE)
            helper.succeed()
        }
    }

    /**
     * The owner and the owner's tamed wolf stand 2 blocks from the primary target, another zombie 3 blocks:
     * the chain takes the zombie, and from there finds nobody else it may hit.
     */
    fun chainSkipsOwnerAndPet(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        val primary = zombie(helper, BlockPos(4, 1, 4))
        val other = zombie(helper, BlockPos(4, 1, 7))
        val owner = mockServerPlayer(helper, Vec3(2.5, 1.0, 4.5))
        val wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, 6, 1, 4).apply { tame(owner) }
        val spared = listOf<LivingEntity>(owner, wolf)
        var before = emptyList<Float>()
        helper.runAfterDelay(THROW_TICK) {
            // A mock player can't be hurt until its client counts as loaded: then sparing it would prove nothing
            val probe = DischargeTargets.source(helper.level, ModDamageTypes.DISCHARGE_CHAIN, originOf(helper, null))
            helper.assertTrue(!owner.isInvulnerableTo(helper.level, probe), "the owner can't be hurt at all")
            before = spared.map { it.health }
            throwDown(helper, owner, x = 4, channeling = 6)
        }
        helper.runAfterDelay(CHECK_TICK) {
            val p = ModConfig.params
            assertLost(helper, primary, 8.0 + ChargeMath.dischargeDamage(CHARGE, 1.0, p), TOLERANCE)
            assertLost(helper, other, ChargeMath.chainBase(CHARGE, 1, p), TOLERANCE)
            spared.zip(before).forEach { (entity, health) ->
                helper.assertTrue(entity.health == health, "${entity.displayName.string}: ${entity.health} of $health")
            }
            remove(owner)
            helper.succeed()
        }
    }

    /** Channeling I, no hops: the zombie in the water is hit through it, the one on the shore next to it is not. */
    fun waterHitsOnlyWater(helper: GameTestHelper) {
        pool(helper)
        val primary = zombie(helper, BlockPos(4, 1, 4))
        val swimmer = zombie(helper, BlockPos(6, 1, 4))
        val ashore = zombie(helper, BlockPos(4, 2, 6))
        helper.runAfterDelay(THROW_TICK) { throwDown(helper, DischargeGameTests.mockOwner(helper), x = 4, channeling = 1) }
        helper.runAfterDelay(CHECK_TICK) {
            val p = ModConfig.params
            // Both zombies in the water are wet: M = 1.25
            val d = ChargeMath.dischargeDamage(CHARGE, 1.25, p)
            assertLost(helper, primary, 8.0 + d, TOLERANCE)
            assertLost(helper, swimmer, ChargeMath.spreadDamage(CHARGE, 2.0, ChargeMath.spreadRadius(1, p), 1.25, p), TOLERANCE)
            assertLost(helper, ashore, 0.0, 1e-3)
            helper.succeed()
        }
    }

    /**
     * Channeling VI: the water takes the swimmer, the chain then takes the zombie ashore, and its second hop
     * finds only zombies already hit. Nobody takes two hits, the swimmer loses exactly its water hit.
     */
    fun oneHitEach(helper: GameTestHelper) {
        pool(helper)
        val primary = zombie(helper, BlockPos(4, 1, 4))
        val swimmer = zombie(helper, BlockPos(6, 1, 4))
        val ashore = zombie(helper, BlockPos(4, 2, 6))
        helper.runAfterDelay(THROW_TICK) { throwDown(helper, DischargeGameTests.mockOwner(helper), x = 4, channeling = 6) }
        helper.runAfterDelay(CHECK_TICK) {
            val p = ModConfig.params
            val d = ChargeMath.dischargeDamage(CHARGE, 1.25, p)
            assertLost(helper, primary, 8.0 + d, TOLERANCE)
            assertLost(helper, swimmer, ChargeMath.spreadDamage(CHARGE, 2.0, ChargeMath.spreadRadius(6, p), 1.25, p), TOLERANCE)
            assertLost(helper, ashore, ChargeMath.chainBase(CHARGE, 1, p), TOLERANCE)
            helper.succeed()
        }
    }

    /** Two pools behind a wall: within the radius both count, as one water body only the near pool does. */
    fun waterSameBody(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        for (x in 0..8) for (z in 2..6) {
            val water = (x in 1..3 || x in 5..7) && z in 3..5
            helper.setBlock(x, 1, z, if (water) Blocks.WATER else Blocks.STONE)
        }
        val primary = zombie(helper, BlockPos(2, 1, 4))
        val near = zombie(helper, BlockPos(3, 1, 3))
        val behindWall = zombie(helper, BlockPos(5, 1, 4))
        helper.runAfterDelay(5) {
            val level = helper.level
            val any = WaterSpread.targets(level, primary, 4.0, floodLimit = 0) { true }
            helper.assertTrue(any == listOf(near, behindWall), "all water: ${any.map { it.blockPosition() }}")
            val sameBody = WaterSpread.targets(level, primary, 4.0, ModConfig.params.spreadFloodLimit) { true }
            helper.assertTrue(sameBody == listOf(near), "same body: ${sameBody.map { it.blockPosition() }}")
            helper.succeed()
        }
    }

    /** `deathMessages`: who hears of a death besides those vanilla tells. */
    fun deathMessageModes(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        val owner = mockServerPlayer(helper, Vec3(1.5, 1.0, 1.5))
        val stranger = mockServerPlayer(helper, Vec3(7.5, 1.0, 7.5))
        val players = listOf(owner, stranger)
        val plain = zombie(helper, BlockPos(2, 1, 4))
        val named = zombie(helper, BlockPos(4, 1, 4)).apply { customName = Component.literal("Bob") }
        val pet: Wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, 6, 1, 4).apply { tame(owner) }

        fun check(victim: LivingEntity, mode: DeathMessageMode, expected: List<ServerPlayer>, show: Boolean = true) {
            val heard = DeathAnnouncer.recipients(victim, mode, show, players)
            helper.assertTrue(heard == expected, "$mode, ${victim.displayName.string}: ${heard.map { it.displayName.string }}")
        }

        for (victim in listOf(plain, named, pet, stranger)) check(victim, DeathMessageMode.VANILLA, emptyList())
        check(plain, DeathMessageMode.NAMED, emptyList())
        check(named, DeathMessageMode.NAMED, players)
        // Vanilla tells the owner of a tamed animal itself
        check(pet, DeathMessageMode.NAMED, listOf(stranger))
        check(plain, DeathMessageMode.ALL, players)
        check(pet, DeathMessageMode.ALL, listOf(stranger))
        // Vanilla tells everyone of a player's death
        check(stranger, DeathMessageMode.ALL, emptyList())
        check(named, DeathMessageMode.ALL, emptyList(), show = false)
        players.forEach(::remove)
        helper.succeed()
    }

    /** Stone floor and a pool of still water x 3..7, z 3..5 at y = 1 in a stone rim one block high. */
    private fun pool(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        for (x in 2..8) for (z in 2..6) helper.setBlock(x, 1, z, if (x in 3..7 && z in 3..5) Blocks.WATER else Blocks.STONE)
    }

    /** A trident of [owner] falling onto the head of the zombie at block x = [x], z = 4. */
    private fun throwDown(helper: GameTestHelper, owner: Player, x: Int, channeling: Int): ThrownTrident =
        DischargeGameTests.throwAt(
            helper, owner, CHARGE, from = Vec3(x + 0.5, 3.6, 4.5), velocity = Vec3(0.0, -1.0, 0.0), channeling = channeling,
        )

    /**
     * A survival player in the level that can be hurt: its client counts as loaded, and it has no Apothic Attributes
     * crits. It skips the player list login of `makeMockServerPlayerInLevel`, where Apothic Attributes sends its
     * config to a test connection that can't carry mod payloads.
     */
    internal fun mockServerPlayer(helper: GameTestHelper, pos: Vec3): ServerPlayer {
        val level = helper.level
        val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), "test-player"), false)
        val player = ServerPlayer(level.server, level, cookie.gameProfile(), cookie.clientInformation())
        val connection = Connection(PacketFlow.SERVERBOUND)
        EmbeddedChannel(connection)
        ServerGamePacketListenerImpl(level.server, connection, player, cookie).handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        player.snapTo(helper.absoluteVec(pos))
        player.getAttribute(ALObjects.Attributes.CRIT_CHANCE)!!.baseValue = 0.0
        level.addNewPlayer(player)
        return player
    }

    private fun remove(player: ServerPlayer) = player.discard()

    private fun originOf(helper: GameTestHelper, owner: Player?) =
        DischargeOrigin(helper.level.dimension(), owner?.uuid, owner != null, null, UUID.randomUUID())
}
