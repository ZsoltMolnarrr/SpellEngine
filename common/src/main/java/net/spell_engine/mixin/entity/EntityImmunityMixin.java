package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.spell_engine.api.entity.LivingEntityImmunity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// 1.20.1: `Entity#isInvulnerableTo` has no `LivingEntity` override (1.21 added one), so the spell-granted
/// damage immunity (see {@link LivingEntityImmunityMixin}) is applied on the `Entity` method and limited to
/// living entities through the `Owner` interface the living mixin implements.
@Mixin(Entity.class)
public class EntityImmunityMixin {
    @ModifyReturnValue(method = "isInvulnerableTo", at = @At("RETURN"))
    private boolean isInvulnerableTo_RETURN_SpellEngine_Immunity(boolean original, DamageSource damageSource) {
        if (original) { return true; }
        if ((Object) this instanceof LivingEntityImmunity.Owner owner) {
            var immunities = owner.getImmunities();
            return !immunities.isEmpty() && LivingEntityImmunity.isDamageProtected(immunities, damageSource);
        }
        return false;
    }
}
