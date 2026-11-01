package com.equi.channelingoverhaul.registry

import com.equi.channelingoverhaul.ChannelingOverhaul
import dev.shadowsoffire.apothic_enchanting.ApothicEnchanting
import dev.shadowsoffire.apothic_enchanting.EnchantmentInfo
import dev.shadowsoffire.apothic_enchanting.PowerFunction
import dev.shadowsoffire.placebo.util.RunnableReloader
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.TagsUpdatedEvent

/**
 * Channeling levels in Apothic Enchanting. On 1.21.1 it reads them from `enchantments.cfg` in the player's
 * config folder, which a mod can't ship, and a file made before this mod was installed keeps Channeling at I.
 * So after each load the entry of Channeling is replaced, and Apothic Enchanting syncs it to clients as usual.
 * A `Min Power Function` set for Channeling in that file means someone configured it on purpose: then it stays.
 */
@EventBusSubscriber(modid = ChannelingOverhaul.ID)
object ModEnchantment {
    const val MAX_LEVEL = 31
    const val MAX_LOOT_LEVEL = 1
    const val MIN_POWER = "IF(x <= 25, 4 * x, 100 + 15 * (x - 25))"

    /** What the listener did in the reload under way, for the log line at its end. */
    private class Decision(val channeling: Holder<Enchantment>?, val source: String)

    @Volatile
    private var decision: Decision? = null

    /** Lowest, so this listener runs after the one of Apothic Enchanting, which clears the map and fills it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onReload(event: AddReloadListenerEvent) {
        event.addListener(RunnableReloader.of { decision = apply(event.serverResources.registryLookup) })
    }

    private fun apply(registries: HolderLookup.Provider): Decision {
        val channeling = registries.lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.CHANNELING).orElse(null)
            ?: return Decision(null, "none")
        if (ApothicEnchanting.ENCHANTMENT_INFO[channeling]?.minPower() is PowerFunction.ExpressionPowerFunction) {
            return Decision(channeling, "config")
        }
        ApothicEnchanting.ENCHANTMENT_INFO[channeling] = EnchantmentInfo(
            channeling, MAX_LEVEL, MAX_LOOT_LEVEL, MAX_LEVEL,
            PowerFunction.DefaultMaxPowerFunction.INSTANCE,
            PowerFunction.ExpressionPowerFunction(MIN_POWER),
        )
        return Decision(channeling, "mod")
    }

    /**
     * Fires once a reload is over, after every listener of it, so this reads the entry that really stayed in the map,
     * whoever wrote it. The event also fires on a client that got tags from a server: nothing was reloaded then.
     */
    @SubscribeEvent
    fun onDataLoaded(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val reloaded = decision ?: return
        decision = null
        val info = reloaded.channeling?.let(ApothicEnchanting.ENCHANTMENT_INFO::get)
        if (info == null) {
            ChannelingOverhaul.LOGGER.warn(
                "Channeling has no EnchantmentInfo after the reload (source={}): the enchantment is missing, or something " +
                    "cleared the map after our listener. Apothic Enchanting falls back to the numbers of channeling.json.",
                reloaded.source,
            )
            return
        }
        ChannelingOverhaul.LOGGER.info(
            "Channeling EnchantmentInfo: source={}, maxLevel={}, maxLootLevel={}, minPower(1)={}, minPower(25)={}, minPower(31)={}",
            reloaded.source, info.getMaxLevel(), info.getMaxLootLevel(), info.getMinPower(1), info.getMinPower(25), info.getMinPower(31),
        )
    }
}
