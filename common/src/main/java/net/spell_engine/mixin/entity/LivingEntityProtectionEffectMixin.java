package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.Protection;
import net.spell_engine.api.entity.LivingEntityImmunity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Charge-consuming damage protections ([Protection]) — Paladins' Divine Protection,
/// SkillTree's Cloak of Shadows and Deflection.
///
/// **Why RETURN, and why on `LivingEntity`.**
/// `Protection.tryProtect` is not a query: it plays the pop FX and spends an amplifier level.
/// It must therefore run last, only once every free reason to refuse the hit has been ruled out.
/// `LivingEntity#isInvulnerableTo` is the deepest frame of the invulnerability chain, so RETURN
/// here sits after `Entity#isInvulnerableToBase` (removed / `invulnerable` flag / fire immunity /
/// `FALL_DAMAGE_IMMUNE`) and after `EnchantmentHelper.isImmuneToDamage`. What it does *not* cover
/// is the handful of player-only branches that vanilla evaluates in the outer frames — those are
/// replicated in [Protection#isHitAlreadyBlocked].
///
/// Targeting `LivingEntity` rather than `Player` also lets protection effects work on mobs and
/// summons; every vanilla subclass that overrides `isInvulnerableTo` other than `Player`
/// short-circuits *before* calling `super`, so this hook never sees a hit they already refused.
///
/// **Ordering against [LivingEntityImmunityMixin].** SpellEngine's free immunities modify the
/// return value of the same method. Protection must lose to them, or a hit stopped by e.g. a
/// Paladins barrier would still bill a charge. Two things guarantee that:
/// 1. `priority = 1100` (above the default 1000) makes this mixin apply — and so its
///    `@ModifyReturnValue` handler run — after the immunity one, which then shows up as
///    `original == true` here;
/// 2. the explicit [LivingEntityImmunity#isImmune] re-check below, so the guarantee does not rest
///    on mixin application order alone. It is a scan of a list that is empty for virtually every
///    entity, so the redundancy is free.
@Mixin(value = LivingEntity.class, priority = 1100)
public class LivingEntityProtectionEffectMixin {
    @ModifyReturnValue(method = "isInvulnerableTo", at = @At("RETURN"))
    private boolean isInvulnerableTo_RETURN_SpellEngine_Protection(boolean original, ServerLevel world, DamageSource damageSource) {
        if (original) {
            return true; // Vanilla, or a SpellEngine immunity, already refused the hit — nothing to pay for
        }
        var entity = (LivingEntity) (Object) this;
        if (LivingEntityImmunity.isImmune(entity, damageSource)) {
            return true;
        }
        return Protection.tryProtect(entity, world, damageSource);
    }
}
