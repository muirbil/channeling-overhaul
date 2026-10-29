package com.equi.channelingoverhaul.mixin;

import com.equi.channelingoverhaul.charge.DespawnGuard;
import com.equi.channelingoverhaul.charge.LoyaltyAnchor;
import com.equi.channelingoverhaul.discharge.DischargeHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A trident stuck at a station stays there: no Loyalty return ({@link LoyaltyAnchor}), no despawn ({@link DespawnGuard}).
 * A hit on a mob discharges the trident ({@link DischargeHandler}).
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
    /**
     * Vanilla starts the Loyalty return once {@code dealtDamage} is set, which happens 4 ticks after the
     * trident sticks. A trident {@link LoyaltyAnchor} holds reads it as false and stays until picked up;
     * a return already under way ({@code isNoPhysics}) goes on as usual.
     */
    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;dealtDamage:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean channeling_overhaul$holdInConductor(boolean dealtDamage) {
        return dealtDamage && !LoyaltyAnchor.holds((ThrownTrident) (Object) this);
    }

    /** Vanilla skips the despawn timer only for Loyalty; {@link DespawnGuard} skips it for Channeling too. */
    @Inject(method = "tickDespawn", at = @At("HEAD"), cancellable = true)
    private void channeling_overhaul$keepWithChanneling(CallbackInfo ci) {
        if (DespawnGuard.keeps((ThrownTrident) (Object) this)) ci.cancel();
    }

    /**
     * {@code onHitEntity} reaches {@code doPostHurtEffects} only when the trident's own damage went through
     * ({@code hurtOrSimulate} returned true, the target is living and not an enderman dodging it), after the
     * vanilla enchantment effects and the knockback. A blocked or refused hit keeps the charge.
     */
    @Inject(
        method = "onHitEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;)V",
            shift = At.Shift.AFTER
        )
    )
    private void channeling_overhaul$discharge(EntityHitResult hitResult, CallbackInfo ci) {
        if (hitResult.getEntity() instanceof LivingEntity target) {
            DischargeHandler.onTridentHit((ThrownTrident) (Object) this, target);
        }
    }
}
