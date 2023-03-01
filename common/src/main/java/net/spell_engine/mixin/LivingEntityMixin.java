package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.internals.SpellCasterClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    // MARK: Spell casting controls

    @Shadow public abstract int getItemUseTimeLeft();

    @Inject(method = "clearActiveItem", at = @At("HEAD"))
    private void clearActiveItem_HEAD_SpellEngine(CallbackInfo ci) {
        var entity = (LivingEntity) ((Object) this);
        if (entity.world.isClient && entity instanceof SpellCasterClient caster) {
            if (caster.getCurrentSpellId() != null) {
                // System.out.println("Client clearActiveItem (castRelease)" + " | time: " + entity.age);
                caster.castRelease(entity.getActiveItem(), entity.getActiveHand(), getItemUseTimeLeft());
            }
        }
    }
}
