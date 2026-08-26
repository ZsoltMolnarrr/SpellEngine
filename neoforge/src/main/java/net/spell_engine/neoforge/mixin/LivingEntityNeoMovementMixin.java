package net.spell_engine.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityNeoMovementMixin {
    // NeoForge completely rewrites slipperiness handling, hence the custom mixin
    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"), require = 0)
    private float getFriction_Wrapped(BlockState instance, LevelReader worldView, BlockPos blockPos, Entity entity, Operation<Float> original) {
        var result = original.call(instance, worldView, blockPos, entity);
        // var entity = (LivingEntity) (Object) this;
        if (entity instanceof SpellCaster.Player caster) {
            // result = Math.max(result - (caster.getExtraSlipperiness() * 0.5F), 0F);
//            if (caster.getExtraSlipperiness() != 0F) {
//                result = Math.min(result + caster.getExtraSlipperiness(), 1F);
//            }
            result = Math.min(result + caster.getExtraSlipperiness(), 1F);
        }
        return result;
    }
}
