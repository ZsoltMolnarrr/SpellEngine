package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.spell_engine.api.effect.OnRemoval;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityEffectRemoval {
    @WrapOperation(method = "onEffectsRemoved", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;removeAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;)V"))
    private void onStatusEffectRemoved_Wrap_onRemoved(MobEffect instance, AttributeMap attributeContainer, Operation<Void> original) {
        original.call(instance, attributeContainer);
        var entity = (LivingEntity) (Object) this;
        var handler = ((OnRemoval) instance).removalHandler();
        if (handler != null) {
            handler.accept(new OnRemoval.Context(entity));
        }
    }
}