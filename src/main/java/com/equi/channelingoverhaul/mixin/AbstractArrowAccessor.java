package com.equi.channelingoverhaul.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code AbstractArrow.isInGround()} is protected, the hit block and the despawn timer are private. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Invoker("isInGround")
    boolean channeling_overhaul$isInGround();

    /** The block the arrow hit last, saved with the entity; null on the client unless it saw the hit. */
    @Accessor("lastState")
    @Nullable BlockState channeling_overhaul$getLastState();

    /** Despawn timer of a stuck arrow, gone at 1200; game tests set it close to that. */
    @Accessor("life")
    void channeling_overhaul$setLife(int life);
}
