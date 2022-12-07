package net.combatspells.mixin;

import net.combatspells.CombatSpells;
import net.combatspells.entity.ChannelTarget;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements ChannelTarget {
    private boolean isHitByChanneling = false;

    @Override
    public void setHitByChanneling(boolean value) {
        isHitByChanneling = value;
    }

    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public double takeKnockback_HEAD_changeStrength(double knockbackStrength) {
        if (isHitByChanneling && CombatSpells.config != null) {
            return knockbackStrength * CombatSpells.config.getChannelledSpellsKnockback();
        }
        return knockbackStrength;
    }
}
