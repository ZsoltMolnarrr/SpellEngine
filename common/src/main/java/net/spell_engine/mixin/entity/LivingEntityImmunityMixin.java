package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.spell_engine.api.entity.LivingEntityImmunity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public class LivingEntityImmunityMixin implements LivingEntityImmunity.Owner {
    @Unique
    private final ArrayList<LivingEntityImmunity.Entry> immunities = new ArrayList<>();
    @Override
    public void addImmunity(LivingEntityImmunity.Entry entry) {
        if (entry.validUntil() > 0) {
            immunities.add(entry);
        }
    }

    @Override
    public List<LivingEntityImmunity.Entry> getImmunities() {
        return immunities;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellEngine_Immunity(CallbackInfo ci) {
        if (immunities.isEmpty()) return;
        var entity = (LivingEntity) ((Object) this);
        var age = entity.age;
        immunities.removeIf(entry -> age > entry.validUntil());
    }

    @ModifyReturnValue(method = "isInvulnerableTo", at = @At("RETURN"))
    private boolean isInvulnerableTo_RETURN_SpellEngine_Immunity(boolean original, DamageSource damageSource) {
        return original || LivingEntityImmunity.isDamageProtected(immunities, damageSource);
    }

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void addStatusEffect_HEAD_SpellEngine_Immunity(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        var harmful = !effect.getEffectType().value().isBeneficial();
        for (var entry: immunities) {
            if (harmful && entry.effectAnyHarmful()) {
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
        }
    }
}
