package net.combatspells.mixin;

import net.combatspells.entity.LivingEntityExtension;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements LivingEntityExtension {
    private float customKnockbackMultiplier = 1;

    @Override
    public void setKnockbackMultiplier(float value) {
        customKnockbackMultiplier = value;
    }

    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public double takeKnockback_HEAD_changeStrength(double knockbackStrength) {
        return knockbackStrength * customKnockbackMultiplier;
    }
}
