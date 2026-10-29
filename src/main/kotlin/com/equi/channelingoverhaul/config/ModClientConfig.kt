package com.equi.channelingoverhaul.config

import net.neoforged.neoforge.common.ModConfigSpec
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT
import net.neoforged.fml.config.ModConfig as FmlModConfig

/** How many of the mod's particles a client draws, on top of the vanilla particle setting. */
enum class ParticleIntensity(val factor: Double) {
    ALL(1.0),
    REDUCED(0.4),
    MINIMAL(0.1),
}

/**
 * Client config: only the look. Particles go through the vanilla `addParticle`, so the vanilla
 * "Particles" option thins them out as well; this scales how many the mod starts in the first place.
 */
object ModClientConfig {
    private val BUILDER = ModConfigSpec.Builder()

    private val particles = BUILDER
        .comment("Particles of charging, arcs and discharges: all, reduced or minimal. The vanilla particle setting applies on top")
        .defineEnum("particles", ParticleIntensity.ALL)

    val SPEC: ModConfigSpec = BUILDER.build()

    /** Factor of the current setting; all particles until the config is loaded. */
    val intensity: Double
        get() = if (SPEC.isLoaded) particles.get().factor else ParticleIntensity.ALL.factor

    fun register() {
        LOADING_CONTEXT.activeContainer.registerConfig(FmlModConfig.Type.CLIENT, SPEC)
    }
}
