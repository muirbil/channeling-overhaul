package com.equi.channelingoverhaul.gametest

import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.core.ChargeMath
import com.equi.channelingoverhaul.data.TridentCharge
import com.equi.channelingoverhaul.discharge.DischargeBolt
import com.equi.channelingoverhaul.discharge.DischargeHandler
import com.equi.channelingoverhaul.discharge.DischargeTargets
import com.equi.channelingoverhaul.registry.ModDamageTypes
import com.equi.channelingoverhaul.registry.ModDataMaps
import com.equi.channelingoverhaul.registry.ModEntities
import dev.shadowsoffire.apothic_attributes.api.ALObjects
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Discharge of a thrown Channeling I trident (Qmax 16) into a zombie without armor points, so the health
 * it loses is the trident's 8 plus D exactly. Fire resistance keeps the zombie from burning in the sun.
 */
object DischargeGameTests {
    /** `ThrownTrident.onHitEntity` */
    private const val TRIDENT_DAMAGE = 8.0
    private const val THROW_TICK = 10L
    private const val CHECK_TICK = 30L

    /** Q = 6 of 16, a weak discharge: 8 + 0.6·Q, the charge is spent and no lightning comes down. */
    fun dischargeIntoZombie(helper: GameTestHelper) {
        val zombie = room(helper)
        val bolts = countAppearing(helper, DischargeBolt::class.java)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(THROW_TICK) { trident = throwDown(helper, mockOwner(helper), charge = 6.0) }
        helper.runAfterDelay(CHECK_TICK) {
            val stack = trident?.pickupItemStackOrigin ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(TridentCharge.stored(stack) == 0.0, "charge left: ${TridentCharge.stored(stack)}")
            // One tick of flight leaks 0.003 of the 6
            assertLost(helper, zombie, TRIDENT_DAMAGE + ChargeMath.dischargeDamage(6.0, 1.0, ModConfig.params), tolerance = 0.01)
            helper.assertTrue(bolts.get() == 0, "a weak discharge drew ${bolts.get()} bolts")
            helper.succeed()
        }
    }

    /**
     * Full charge: one [DischargeBolt] for a few ticks and no vanilla lightning, so the exposed copper
     * under the zombie stays exposed and nothing catches fire.
     */
    fun strongDischargeBolt(helper: GameTestHelper) {
        val zombie = room(helper, under = Blocks.EXPOSED_COPPER)
        val bolts = countAppearing(helper, DischargeBolt::class.java)
        val lightning = countAppearing(helper, LightningBolt::class.java)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(THROW_TICK) { trident = throwDown(helper, mockOwner(helper), charge = 16.0) }
        helper.runAfterDelay(CHECK_TICK) {
            val stack = trident?.pickupItemStackOrigin ?: return@runAfterDelay helper.fail("no trident")
            helper.assertTrue(TridentCharge.stored(stack) == 0.0, "charge left: ${TridentCharge.stored(stack)}")
            assertLost(helper, zombie, TRIDENT_DAMAGE + ChargeMath.dischargeDamage(16.0, 1.0, ModConfig.params), tolerance = 0.02)
            helper.assertTrue(bolts.get() == 1, "drew ${bolts.get()} bolts, expected 1")
            helper.assertTrue(lightning.get() == 0, "struck ${lightning.get()} vanilla bolts")
            // Thrown at tick 10 and gone 8 ticks after the hit
            helper.assertEntityNotPresent(ModEntities.DISCHARGE_BOLT)
            helper.assertBlockPresent(Blocks.EXPOSED_COPPER, ZOMBIE_POS.below())
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(ZOMBIE_POS)).isAir, "fire at the zombie")
            helper.succeed()
        }
    }

    /** Q = 2, below the threshold of 3: only the trident's own damage, the charge stays. */
    fun belowThresholdKeepsCharge(helper: GameTestHelper) {
        val zombie = room(helper)
        var trident: ThrownTrident? = null
        helper.runAfterDelay(THROW_TICK) { trident = throwDown(helper, mockOwner(helper), charge = 2.0) }
        helper.runAfterDelay(CHECK_TICK) {
            assertKept(helper, trident, 2.0)
            assertLost(helper, zombie, TRIDENT_DAMAGE, tolerance = 1e-3)
            helper.succeed()
        }
    }

    /** A shield blocks the trident: no damage at all, the charge stays in the trident. */
    fun shieldKeepsCharge(helper: GameTestHelper) {
        val zombie = room(helper)
        zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack(Items.SHIELD))
        zombie.startUsingItem(InteractionHand.OFF_HAND)
        var trident: ThrownTrident? = null
        // The shield blocks after 5 ticks of use
        helper.runAfterDelay(THROW_TICK) {
            helper.assertTrue(zombie.isBlocking, "the zombie doesn't block")
            trident = throwAt(helper, mockOwner(helper), charge = 10.0, from = Vec3(4.5, 2.2, 7.5), velocity = Vec3(0.0, 0.0, -1.2))
        }
        helper.runAfterDelay(CHECK_TICK) {
            assertKept(helper, trident, 10.0)
            assertLost(helper, zombie, 0.0, tolerance = 1e-3)
            helper.succeed()
        }
    }

    /** M of real targets: worn pieces by the data map, water adds 0.25, and only a piece with a bonus makes a conductor. */
    fun armorAndWater(helper: GameTestHelper) {
        stone(helper, 0, 0..8, 0..8)
        // A pool of one block, so the water doesn't reach the dry zombie
        for (pos in listOf(BlockPos(1, 1, 4), BlockPos(3, 1, 4), BlockPos(2, 1, 3), BlockPos(2, 1, 5))) helper.setBlock(pos, Blocks.STONE)
        helper.setBlock(2, 1, 4, Blocks.WATER)
        val wet = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4)
        wet.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.GOLDEN_HELMET))
        wet.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.COPPER_BOOTS))
        wet.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        val dry = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 1, 4)
        dry.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.TURTLE_HELMET))
        dry.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.LEATHER_CHESTPLATE))
        dry.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.DIAMOND_LEGGINGS))
        helper.runAfterDelay(5) {
            val p = ModConfig.params
            val wetBonuses = DischargeTargets.armorBonuses(wet)
            helper.assertTrue(wetBonuses.sorted() == listOf(0.15, 0.15), "wet zombie armor: $wetBonuses")
            helper.assertTrue(wet.isInWaterOrRain, "the zombie is not wet")
            val wetHit = ChargeMath.discharge(10.0, 16.0, wetBonuses, wet.isInWaterOrRain, p)!!
            helper.assertTrue(abs(wetHit.vulnerability - 1.55) < 1e-9 && wetHit.conductor, "wet zombie: $wetHit")
            val dryBonuses = DischargeTargets.armorBonuses(dry)
            helper.assertTrue(dryBonuses == listOf(0.0, 0.0, 0.0), "dry zombie armor: $dryBonuses")
            val dryHit = ChargeMath.discharge(10.0, 16.0, dryBonuses, dry.isInWaterOrRain, p)!!
            helper.assertTrue(dryHit.vulnerability == 1.0 && !dryHit.conductor, "dry zombie: $dryHit")
            helper.succeed()
        }
    }

    /** Every vanilla piece of humanoid armor is in `armor_conductivity`, with the bonus of its material. */
    fun armorConductivityCoversArmor(helper: GameTestHelper) {
        val bonusByMaterial = listOf(
            "golden_" to 0.15, "copper_" to 0.15, "netherite_" to 0.12, "iron_" to 0.10, "chainmail_" to 0.10,
            "leather_" to 0.0, "diamond_" to 0.0, "turtle_" to 0.0,
        )
        val wrong = mutableListOf<String>()
        var checked = 0
        for (item in BuiltInRegistries.ITEM) {
            val id = BuiltInRegistries.ITEM.getKey(item)
            val stack = ItemStack(item)
            val slot = stack.get(DataComponents.EQUIPPABLE)?.slot() ?: continue
            val armor = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)?.modifiers()
                ?.any { it.attribute() == Attributes.ARMOR && it.modifier().amount() > 0.0 } ?: false
            if (id.namespace != "minecraft" || slot.type != EquipmentSlot.Type.HUMANOID_ARMOR || !armor) continue
            checked++
            val expected = bonusByMaterial.firstOrNull { id.path.startsWith(it.first) }?.second
            val actual = stack.typeHolder().getData(ModDataMaps.ARMOR_CONDUCTIVITY)
            if (expected == null || actual != expected) wrong += "${id.path}: $actual, expected $expected"
        }
        // 7 materials x 4 pieces + the turtle shell
        helper.assertTrue(checked == 29, "checked $checked armor pieces")
        helper.assertTrue(wrong.isEmpty(), wrong.joinToString("; "))
        helper.succeed()
    }

    /** Death messages come through the combat tracker, as on a real death, with the killer and the trident's name. */
    fun deathMessages(helper: GameTestHelper) {
        val zombie = room(helper)
        val level = helper.getLevel()
        val owner = mockOwner(helper)
        val p = ModConfig.params

        fun message(source: DamageSource): TranslatableContents {
            helper.assertTrue(zombie.hurtServer(level, source, 0.5f), "the zombie took no damage")
            val contents = zombie.combatTracker.deathMessage.contents as TranslatableContents
            helper.assertTrue(Language.getInstance().has(contents.key), "no translation for ${contents.key}")
            return contents
        }

        fun check(name: String, source: DamageSource, key: String, vararg args: String) {
            val contents = message(source)
            val actual = contents.args.map { (it as Component).string }
            helper.assertTrue(contents.key == "death.attack.channeling_overhaul.discharge$key", "$name: key ${contents.key}")
            helper.assertTrue(actual.take(args.size) == args.toList(), "$name: args $actual")
        }

        val victim = zombie.displayName.string
        val killer = owner.displayName.string
        val plain = tridentStack(helper, charge = 0.0)
        val named = tridentStack(helper, charge = 0.0).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Zeus")) }
        val thrown = ThrownTrident(level, owner, plain)
        val unowned = ThrownTrident(level, 0.0, 0.0, 0.0, plain)

        fun source(trident: ThrownTrident, stack: ItemStack, q: Double, armor: List<Double>) =
            DischargeHandler.source(level, trident, ChargeMath.discharge(q, 16.0, armor, false, p)!!, stack)

        check("plain", source(thrown, plain, 5.0, emptyList()), ".player", victim, killer)
        check("named", source(thrown, named, 5.0, emptyList()), ".item", victim, killer, "Zeus")
        check("conductor", source(thrown, named, 5.0, listOf(0.1)), ".conductor", victim, killer)
        check("overcharged beats conductor", source(thrown, plain, 15.0, listOf(0.1)), ".overcharged", victim, killer)
        check("no owner", source(unowned, plain, 5.0, emptyList()), "", victim)
        // A full charge into a zombie in a gold set: only the direct discharge is "vaporized" or "metal"
        for (type in listOf(ModDamageTypes.DISCHARGE_CHAIN, ModDamageTypes.DISCHARGE_WATER)) {
            val suffix = type.identifier().path.removePrefix("discharge")
            check(suffix, DischargeTargets.source(level, type, thrown, owner), "$suffix.player", victim, killer)
            check("$suffix, no owner", DischargeTargets.source(level, type, unowned, null), suffix, victim)
        }
        helper.succeed()
    }

    private val ZOMBIE_POS = BlockPos(4, 1, 4)

    /** Stone floor, a zombie facing +z in the middle with no armor points, standing on [under]. */
    private fun room(helper: GameTestHelper, under: Block = Blocks.STONE): Zombie {
        stone(helper, 0, 0..8, 0..8)
        helper.setBlock(ZOMBIE_POS.below(), under)
        return zombie(helper, ZOMBIE_POS)
    }

    /** A zombie facing +z with no armor points that doesn't burn in the sun. */
    internal fun zombie(helper: GameTestHelper, pos: BlockPos): Zombie {
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos)
        zombie.getAttribute(Attributes.ARMOR)!!.baseValue = 0.0
        zombie.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200))
        zombie.yRot = 0f
        zombie.yHeadRot = 0f
        zombie.yBodyRot = 0f
        return zombie
    }

    internal fun stone(helper: GameTestHelper, y: Int, xs: IntRange, zs: IntRange) {
        for (x in xs) for (z in zs) helper.setBlock(x, y, z, Blocks.STONE)
    }

    /**
     * The trident kept its [charge]: after the hit it bounces off and falls, so at most the leak of that flight
     * is gone, and once stuck it may charge a little from the sky.
     */
    private fun assertKept(helper: GameTestHelper, trident: ThrownTrident?, charge: Double) {
        val stack = trident?.pickupItemStackOrigin ?: return helper.fail("no trident")
        val afterFlight = ChargeMath.leak(charge, ChargeMath.ticksToSeconds(CHECK_TICK - THROW_TICK), ModConfig.params)
        val actual = TridentCharge.stored(stack)
        helper.assertTrue(actual >= afterFlight, "charge: $actual, at least $afterFlight")
    }

    internal fun assertLost(helper: GameTestHelper, entity: LivingEntity, expected: Double, tolerance: Double) {
        val lost = entity.maxHealth - entity.health
        helper.assertTrue(abs(lost - expected) < tolerance, "${entity.displayName.string} lost $lost health, expected $expected")
    }

    /** Entities of [type] that appear in the test area, counted once each. */
    private fun countAppearing(helper: GameTestHelper, type: Class<out Entity>): AtomicInteger {
        val seen = mutableSetOf<Int>()
        val count = AtomicInteger()
        val area = AABB(helper.absolutePos(BlockPos(0, 0, 0)).center, helper.absolutePos(BlockPos(8, 4, 8)).center).inflate(1.0)
        helper.onEachTick {
            for (entity in helper.getLevel().getEntitiesOfClass(type, area)) {
                if (seen.add(entity.id)) count.incrementAndGet()
            }
        }
        return count
    }

    /**
     * Not in the level, so it neither ticks nor picks anything up by itself. Without the 5% crit chance
     * Apothic Attributes gives every player, its trident and discharge deal exact damage.
     */
    internal fun mockOwner(helper: GameTestHelper): Player =
        helper.makeMockPlayer(GameType.SURVIVAL).apply {
            setPos(helper.absoluteVec(Vec3(4.5, 1.0, 8.5)))
            getAttribute(ALObjects.Attributes.CRIT_CHANCE)!!.baseValue = 0.0
        }

    private fun tridentStack(helper: GameTestHelper, charge: Double, channeling: Int = 1): ItemStack {
        val stack = ItemStack(Items.TRIDENT)
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING), channeling)
        TridentCharge.write(stack, charge, helper.getLevel().gameTime)
        return stack
    }

    /** A Channeling I trident of [owner] falling onto the zombie's head. */
    private fun throwDown(helper: GameTestHelper, owner: Player, charge: Double) =
        throwAt(helper, owner, charge, from = Vec3(4.5, 3.6, 4.5), velocity = Vec3(0.0, -1.0, 0.0))

    /** A trident with Channeling [channeling] and [charge] thrown by [owner] from [from], relative to the test. */
    internal fun throwAt(
        helper: GameTestHelper, owner: Player, charge: Double, from: Vec3, velocity: Vec3, channeling: Int = 1,
    ): ThrownTrident {
        val level = helper.getLevel()
        val trident = ThrownTrident(level, owner, tridentStack(helper, charge, channeling))
        trident.setPos(helper.absoluteVec(from))
        trident.deltaMovement = velocity
        level.addFreshEntity(trident)
        return trident
    }
}
