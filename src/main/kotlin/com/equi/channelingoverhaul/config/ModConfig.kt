package com.equi.channelingoverhaul.config

import com.equi.channelingoverhaul.core.ChargeParams
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import net.neoforged.fml.config.ModConfig as FmlModConfig

/**
 * Server config with the balance numbers. NeoForge syncs it to clients.
 * Game code reads [params], a snapshot rebuilt on every load and reload,
 * instead of the spec values. Defaults come from [ChargeParams].
 */
object ModConfig {
    private val DEFAULTS = ChargeParams()
    private val RULES = DischargeRules()
    private val BUILDER = ModConfigSpec.Builder()

    init {
        BUILDER.comment("Capacity, gathering radius, leak and charging intervals").push("charge")
    }

    private val capacityBase = BUILDER
        .comment("Qmax(L) = capacityBase + capacityPerLevel * L")
        .defineInRange("capacityBase", DEFAULTS.capacityBase, 0.0, 10_000.0)
    private val capacityPerLevel = BUILDER
        .defineInRange("capacityPerLevel", DEFAULTS.capacityPerLevel, 0.0, 1_000.0)
    private val radiusBase = BUILDER
        .comment("R(L) = min(radiusMax, radiusBase + radiusPerSqrtLevel * sqrt(L)), blocks")
        .defineInRange("radiusBase", DEFAULTS.radiusBase, 0.0, 64.0)
    private val radiusPerSqrtLevel = BUILDER
        .defineInRange("radiusPerSqrtLevel", DEFAULTS.radiusPerSqrtLevel, 0.0, 64.0)
    private val radiusMax = BUILDER
        .comment("Hard cap of the radius, keeps block scans cheap")
        .defineInRange("radiusMax", DEFAULTS.radiusMax, 1.0, 32.0)
    private val leakHalfLife = BUILDER
        .comment("Half-life of the charge in the inventory, in hand and in flight, seconds")
        .defineInRange("leakHalfLife", DEFAULTS.leakHalfLife, 1.0, 1_000_000.0)
    private val chargeIntervalTicks = BUILDER
        .comment("A stuck trident charges once per this many ticks")
        .defineInRange("chargeIntervalTicks", DEFAULTS.chargeIntervalTicks, 1, 1_200)
    private val rescanInterval = BUILDER
        .comment("A stuck trident looks for copper, lightning rods and water again after this many seconds")
        .defineInRange("rescanInterval", DEFAULTS.rescanInterval, 0.5, 600.0)

    init {
        BUILDER.pop().comment("Sky inflow S, charge per second, only under open sky").push("sky")
    }

    private val skyClear = BUILDER
        .comment("Clear weather, and biomes without precipitation in any weather")
        .defineInRange("clear", DEFAULTS.skyClear, 0.0, 100.0)
    private val skyRain = BUILDER.defineInRange("rain", DEFAULTS.skyRain, 0.0, 100.0)
    private val skySnow = BUILDER.defineInRange("snow", DEFAULTS.skySnow, 0.0, 100.0)
    private val skyThunderRain = BUILDER.defineInRange("thunderRain", DEFAULTS.skyThunderRain, 0.0, 100.0)
    private val skyThunderSnow = BUILDER.defineInRange("thunderSnow", DEFAULTS.skyThunderSnow, 0.0, 100.0)

    init {
        BUILDER.pop()
            .comment(
                "Antenna multiplier A = min(max, 1 + sum(copper * c * p) + sum(rodFalloff^k * rod * p_k) + min(waterMax, sum(water * p))),",
                "p = 1 - distance / R, k numbers lightning rods from the closest one",
            )
            .push("antenna")
    }

    private val antennaMax = BUILDER.defineInRange("max", DEFAULTS.antennaMax, 1.0, 100.0)
    private val copperWeight = BUILDER.defineInRange("copper", DEFAULTS.copperWeight, 0.0, 10.0)
    private val rodWeight = BUILDER.defineInRange("rod", DEFAULTS.rodWeight, 0.0, 10.0)
    private val rodFalloff = BUILDER
        .comment("Each next lightning rod gives this fraction of the previous one")
        .defineInRange("rodFalloff", DEFAULTS.rodFalloff, 0.0, 1.0)
    private val antennaWaterWeight = BUILDER.defineInRange("water", DEFAULTS.antennaWaterWeight, 0.0, 10.0)
    private val antennaWaterMax = BUILDER.defineInRange("waterMax", DEFAULTS.antennaWaterMax, 0.0, 10.0)

    init {
        BUILDER.pop().comment("Lightning strike within the radius of a stuck trident, fraction of Qmax").push("lightning")
    }

    private val strikeRodBonus = BUILDER.defineInRange("rodBonus", DEFAULTS.strikeRodBonus, 0.0, 1.0)
    private val strikeNearbyBonus = BUILDER.defineInRange("nearbyBonus", DEFAULTS.strikeNearbyBonus, 0.0, 1.0)

    init {
        BUILDER.pop().comment("Recharge while holding right click").push("windup")
    }

    private val windupFactor = BUILDER
        .comment("Inflow at the player position is multiplied by this")
        .defineInRange("factor", DEFAULTS.windupFactor, 0.0, 10.0)
    private val windupCap = BUILDER
        .comment("Windup never charges above this fraction of Qmax")
        .defineInRange("cap", DEFAULTS.windupCap, 0.0, 1.0)
    private val windupRescanInterval = BUILDER
        .comment("A winding up player looks for copper, lightning rods and water around them at most this often, seconds")
        .defineInRange("rescanInterval", DEFAULTS.windupRescanInterval, 0.05, 60.0)

    init {
        BUILDER.pop().comment("Discharge into a mob: D = damagePerCharge * Q * M").push("discharge")
    }

    private val damagePerCharge = BUILDER.defineInRange("damagePerCharge", DEFAULTS.damagePerCharge, 0.0, 100.0)
    private val minDischargeCharge = BUILDER
        .comment("Below this charge a hit does nothing and the charge stays")
        .defineInRange("minCharge", DEFAULTS.minDischargeCharge, 0.0, 10_000.0)
    private val vulnerabilityMax = BUILDER
        .comment("M = min(vulnerabilityMax, 1 + armor bonuses + wetBonus)")
        .defineInRange("vulnerabilityMax", DEFAULTS.vulnerabilityMax, 1.0, 100.0)
    private val wetBonus = BUILDER.defineInRange("wetBonus", DEFAULTS.wetBonus, 0.0, 10.0)
    private val strongDischarge = BUILDER
        .comment("Fraction of Qmax for the visual lightning and the horn sound")
        .defineInRange("strongDischarge", DEFAULTS.strongDischarge, 0.0, 1.0)
    private val overcharged = BUILDER
        .comment("Fraction of Qmax for the 'vaporized' death message")
        .defineInRange("overcharged", DEFAULTS.overcharged, 0.0, 1.0)
    private val deathMessages = BUILDER
        .comment(
            "Who hears that a mob was killed by a discharge, chain or water, besides the owner of a tamed animal:",
            "vanilla - nobody else, named - everyone for mobs with a name and tamed animals, all - everyone for any mob.",
            "Player deaths are always shown by vanilla. Respects the show_death_messages game rule",
        )
        .defineEnum("deathMessages", RULES.deathMessages)

    init {
        BUILDER.pop()
            .comment(
                "Discharge sound. A strong discharge plays the horn at hornVolumeBase + hornVolumePerCharge * Q / Qmax",
                "(heard from about 16 blocks per unit of volume), a weak one only a quiet crackle",
            )
            .push("sound")
    }

    private val hornVolumeBase = BUILDER.defineInRange("hornVolumeBase", DEFAULTS.hornVolumeBase, 0.0, 100.0)
    private val hornVolumePerCharge = BUILDER.defineInRange("hornVolumePerCharge", DEFAULTS.hornVolumePerCharge, 0.0, 100.0)
    private val crackleVolume = BUILDER.defineInRange("crackleVolume", DEFAULTS.crackleVolume, 0.0, 10.0)
    private val cracklePitch = BUILDER.defineInRange("cracklePitch", DEFAULTS.cracklePitch, 0.5, 2.0)
    private val distantThunder = BUILDER
        .comment("An overcharged discharge (Q >= overcharged * Qmax) of level distantThunderMinLevel and up also thunders far away, for the whole dimension")
        .define("distantThunder", RULES.distantThunder)
    private val distantThunderMinLevel = BUILDER.defineInRange("distantThunderMinLevel", DEFAULTS.distantThunderMinLevel, 1, 127)

    init {
        BUILDER.pop().comment("Chain lightning: jumps N(L) = min(maxJumps, floor(L / levelsPerJump))").push("chain")
    }

    private val chainLevelsPerJump = BUILDER.defineInRange("levelsPerJump", DEFAULTS.chainLevelsPerJump, 1, 127)
    private val chainMaxJumps = BUILDER.defineInRange("maxJumps", DEFAULTS.chainMaxJumps, 0, 64)
    private val chainRange = BUILDER
        .comment("Search range for the next target, blocks")
        .defineInRange("range", DEFAULTS.chainRange, 0.0, 32.0)
    private val chainConductiveRange = BUILDER
        .comment("Search range for a conductive target (M > 1), blocks")
        .defineInRange("conductiveRange", DEFAULTS.chainConductiveRange, 0.0, 32.0)
    private val chainFalloff = BUILDER
        .comment("Each hop deals this fraction of the previous hop, before the target's M")
        .defineInRange("falloff", DEFAULTS.chainFalloff, 0.0, 1.0)
    private val chainHopDelayTicks = BUILDER
        .comment("Ticks between hops, so the chain can be followed by eye")
        .defineInRange("hopDelayTicks", DEFAULTS.chainHopDelayTicks, 1, 100)
    private val chainHitsPlayers = BUILDER
        .comment(
            "With PvP on, the chain and the water of a player's trident hit other players too.",
            "The owner, teammates and everyone while PvP is off are never hit",
        )
        .define("hitsPlayers", RULES.chainHitsPlayers)

    init {
        BUILDER.pop()
            .comment(
                "Spread through water from a primary target standing in water:",
                "r_w = min(radiusMax, radiusBase + radiusPerLevel * L), D_water = factor * damagePerCharge * Q * (1 - d / r_w) * M",
            )
            .push("waterSpread")
    }

    private val spreadRadiusBase = BUILDER.defineInRange("radiusBase", DEFAULTS.spreadRadiusBase, 0.0, 32.0)
    private val spreadRadiusPerLevel = BUILDER.defineInRange("radiusPerLevel", DEFAULTS.spreadRadiusPerLevel, 0.0, 32.0)
    private val spreadRadiusMax = BUILDER.defineInRange("radiusMax", DEFAULTS.spreadRadiusMax, 0.0, 32.0)
    private val spreadFactor = BUILDER.defineInRange("factor", DEFAULTS.spreadFactor, 0.0, 10.0)
    private val sameWaterBody = BUILDER
        .comment("Spread only through the water body of the primary target, instead of all water within r_w")
        .define("sameWaterBody", RULES.sameWaterBody)
    private val spreadFloodLimit = BUILDER
        .comment("With sameWaterBody, at most this many water blocks are filled from the primary target")
        .defineInRange("floodLimit", DEFAULTS.spreadFloodLimit, 1, 100_000)

    init {
        BUILDER.pop().comment("Debug output").push("debug")
    }

    private val dischargeChat = BUILDER
        .comment("Tell the owner of a trident Q, M and D of each discharge, chain hop and water hit in chat")
        .define("dischargeChat", RULES.debugChat)

    init {
        BUILDER.pop()
    }

    val SPEC: ModConfigSpec = BUILDER.build()

    /** Current balance numbers. Defaults until the server config is loaded. */
    @Volatile
    var params: ChargeParams = DEFAULTS
        private set

    /** Current switches. Defaults until the server config is loaded. */
    @Volatile
    var rules: DischargeRules = RULES
        private set

    fun register() {
        LOADING_CONTEXT.activeContainer.registerConfig(FmlModConfig.Type.SERVER, SPEC)
        MOD_BUS.addListener(ModConfigEvent.Loading::class.java, ::onConfigEvent)
        MOD_BUS.addListener(ModConfigEvent.Reloading::class.java, ::onConfigEvent)
        MOD_BUS.addListener(ModConfigEvent.Unloading::class.java, ::onConfigEvent)
    }

    private fun onConfigEvent(event: ModConfigEvent) {
        if (event.config.spec !== SPEC) return
        val unloading = event is ModConfigEvent.Unloading
        params = if (unloading) DEFAULTS else read()
        rules = if (unloading) RULES else readRules()
    }

    private fun readRules() = DischargeRules(
        chainHitsPlayers = chainHitsPlayers.get(),
        sameWaterBody = sameWaterBody.get(),
        deathMessages = deathMessages.get(),
        distantThunder = distantThunder.get(),
        debugChat = dischargeChat.get(),
    )

    private fun read() = ChargeParams(
        capacityBase = capacityBase.get(),
        capacityPerLevel = capacityPerLevel.get(),
        radiusBase = radiusBase.get(),
        radiusPerSqrtLevel = radiusPerSqrtLevel.get(),
        radiusMax = radiusMax.get(),
        leakHalfLife = leakHalfLife.get(),
        chargeIntervalTicks = chargeIntervalTicks.get(),
        rescanInterval = rescanInterval.get(),
        skyClear = skyClear.get(),
        skyRain = skyRain.get(),
        skySnow = skySnow.get(),
        skyThunderRain = skyThunderRain.get(),
        skyThunderSnow = skyThunderSnow.get(),
        antennaMax = antennaMax.get(),
        copperWeight = copperWeight.get(),
        rodWeight = rodWeight.get(),
        rodFalloff = rodFalloff.get(),
        antennaWaterWeight = antennaWaterWeight.get(),
        antennaWaterMax = antennaWaterMax.get(),
        strikeRodBonus = strikeRodBonus.get(),
        strikeNearbyBonus = strikeNearbyBonus.get(),
        windupFactor = windupFactor.get(),
        windupCap = windupCap.get(),
        windupRescanInterval = windupRescanInterval.get(),
        damagePerCharge = damagePerCharge.get(),
        minDischargeCharge = minDischargeCharge.get(),
        vulnerabilityMax = vulnerabilityMax.get(),
        wetBonus = wetBonus.get(),
        strongDischarge = strongDischarge.get(),
        overcharged = overcharged.get(),
        hornVolumeBase = hornVolumeBase.get(),
        hornVolumePerCharge = hornVolumePerCharge.get(),
        crackleVolume = crackleVolume.get(),
        cracklePitch = cracklePitch.get(),
        distantThunderMinLevel = distantThunderMinLevel.get(),
        chainLevelsPerJump = chainLevelsPerJump.get(),
        chainMaxJumps = chainMaxJumps.get(),
        chainRange = chainRange.get(),
        chainConductiveRange = chainConductiveRange.get(),
        chainFalloff = chainFalloff.get(),
        chainHopDelayTicks = chainHopDelayTicks.get(),
        spreadRadiusBase = spreadRadiusBase.get(),
        spreadRadiusPerLevel = spreadRadiusPerLevel.get(),
        spreadRadiusMax = spreadRadiusMax.get(),
        spreadFactor = spreadFactor.get(),
        spreadFloodLimit = spreadFloodLimit.get(),
    )
}
