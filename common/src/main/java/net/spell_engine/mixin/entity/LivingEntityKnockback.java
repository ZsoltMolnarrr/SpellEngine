package net.spell_engine.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.entity.ConfigurableKnockback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Stack;

@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockback implements ConfigurableKnockback {

    /**
     * ConfigurableKnockback
     */

    private Stack<Float> customKnockbackMultipliers = new Stack<>();

    private float getKnockbackMultiplier_SpellEngine() {
        if (customKnockbackMultipliers.isEmpty()) {
            return 1F;
        } else {
            var multiplier = 1F;
            for (var m : customKnockbackMultipliers) {
                multiplier *= m;
            }
            return multiplier;
        }
    }

    public void pushKnockbackMultiplier_SpellEngine(float multiplier) {
        customKnockbackMultipliers.push(multiplier);
    }

    public void popKnockbackMultiplier_SpellEngine() {
        customKnockbackMultipliers.pop();
    }

    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public double takeKnockback_HEAD_changeStrength(double knockbackStrength) {
        return knockbackStrength * getKnockbackMultiplier_SpellEngine();
    }
}
