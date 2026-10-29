package com.equi.channelingoverhaul

import com.equi.channelingoverhaul.config.ModClientConfig
import com.equi.channelingoverhaul.config.ModConfig
import com.equi.channelingoverhaul.network.SparksPayload
import com.equi.channelingoverhaul.registry.ModSounds
import com.equi.channelingoverhaul.registry.ModAttachments
import com.equi.channelingoverhaul.registry.ModDataComponents
import com.equi.channelingoverhaul.registry.ModDataMaps
import com.equi.channelingoverhaul.registry.ModEntities
import net.neoforged.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Main mod object. Registries and event listeners are hooked up here
 * through `MOD_BUS` and `FORGE_BUS` as the mod grows.
 */
@Mod(ChannelingOverhaul.ID)
object ChannelingOverhaul {
    const val ID = "channeling_overhaul"

    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ModConfig.register()
        ModClientConfig.register()
        ModDataComponents.REGISTRY.register(MOD_BUS)
        ModAttachments.REGISTRY.register(MOD_BUS)
        ModEntities.REGISTRY.register(MOD_BUS)
        ModSounds.REGISTRY.register(MOD_BUS)
        SparksPayload.register()
        ModDataMaps.register()
        LOGGER.info("Channeling Overhaul loaded")
    }
}
