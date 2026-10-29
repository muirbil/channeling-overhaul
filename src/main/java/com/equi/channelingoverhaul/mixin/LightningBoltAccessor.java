package com.equi.channelingoverhaul.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code LightningBolt.visualOnly} has a setter only; a visual-only bolt gives a stuck trident no charge. */
@Mixin(LightningBolt.class)
public interface LightningBoltAccessor {
    @Accessor("visualOnly")
    boolean channeling_overhaul$isVisualOnly();
}
