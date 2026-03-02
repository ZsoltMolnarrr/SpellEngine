package net.spell_engine.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityNeoMovementMixin {
    // NeoForge completely rewrites slipperiness handling, hence the custom mixin
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getFriction(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)F"), require = 0)
    private float getFriction_Wrapped(BlockState instance, WorldView worldView, BlockPos blockPos, Entity entity, Operation<Float> original) {
        var result = original.call(instance, worldView, blockPos, entity);
        // var entity = (LivingEntity) (Object) this;
        if (entity instanceof SpellCasterEntity caster) {
            // result = Math.max(result - (caster.getExtraSlipperiness() * 0.5F), 0F);
//            if (caster.getExtraSlipperiness() != 0F) {
//                result = Math.min(result + caster.getExtraSlipperiness(), 1F);
//            }
            result = Math.min(result + caster.getExtraSlipperiness(), 1F);
        }
        return result;
    }
}
