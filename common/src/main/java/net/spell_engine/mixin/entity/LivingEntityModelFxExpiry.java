package net.spell_engine.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Server-side expiry of the model FX attached to a living entity
/// (`SpellEngineAttachments.MODEL_FX`). Nothing vanilla expires them, so they need their own tick;
/// `ModelEffectAttachment.expire` early-returns on an empty list.
@Mixin(LivingEntity.class)
public abstract class LivingEntityModelFxExpiry {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ModelFx(CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) { return; }
        ModelEffectAttachment.expire(entity, entity.level().getGameTime());
    }
}
