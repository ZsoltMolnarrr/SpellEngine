package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMovementMixin {
    // NeoForge completely rewrites slipperiness handling, hence this is optional
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"), require = 0)
    private float getSlipperiness_Wrapped(Block instance, Operation<Float> original) {
        var result = original.call(instance);
        var entity = (LivingEntity) (Object) this;
        if (entity instanceof SpellCasterEntity caster) {
            result = Math.min(result + caster.getExtraSlipperiness(), 1F);
        }
        return result;
    }
}
