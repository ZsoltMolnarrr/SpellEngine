package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.OnRemoval;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityEffectRemoval {
    @WrapOperation(method = "onStatusEffectRemoved", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/effect/StatusEffect;onRemoved(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/attribute/AttributeContainer;I)V"))
    private void onStatusEffectRemoved_Wrap_onRemoved(StatusEffect instance, LivingEntity livingEntity, AttributeContainer attributeContainer, int amplifier, Operation<Void> original) {
        original.call(instance, livingEntity, attributeContainer, amplifier);
        var entity = (LivingEntity) (Object) this;
        var handler = ((OnRemoval) instance).removalHandler();
        if (handler != null) {
            handler.accept(new OnRemoval.Context(entity));
        }
    }
}