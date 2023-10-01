package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.entity.ConfigurableKnockback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ConfigurableKnockback {

    // MARK: ConfigurableKnockback

    private float customKnockbackMultiplier_SpellEngine = 1;

    @Override
    public void setKnockbackMultiplier_SpellEngine(float value) {
        customKnockbackMultiplier_SpellEngine = value;
    }

    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public double takeKnockback_HEAD_changeStrength(double knockbackStrength) {
        return knockbackStrength * customKnockbackMultiplier_SpellEngine;
    }
}
