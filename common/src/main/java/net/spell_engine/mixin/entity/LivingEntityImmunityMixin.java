package net.spell_engine.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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

    // 1.20.1: `isInvulnerableTo` is declared on `Entity` only (LivingEntity does not override it), so the
    // damage-immunity return-value hook lives in `EntityImmunityMixin`, guarded by `instanceof Owner`.

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void addStatusEffect_HEAD_SpellEngine_Immunity(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        var harmful = !effect.getEffectType().isBeneficial();
        for (var entry: immunities) {
            if (harmful && entry.effectAnyHarmful()) {
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
        }
    }
}
