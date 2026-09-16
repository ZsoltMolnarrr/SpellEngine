package net.spell_engine.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// 1.20.1's `RaycastContext` only has the `Entity` constructor, and it calls `ShapeContext.of(entity)`
/// unconditionally, which NPEs for a null entity. 1.21 added a `ShapeContext` constructor, and Spell Engine's
/// ground and obstacle raycasts (`TargetHelper`) pass `ShapeContext.absent()` there when no entity is given —
/// e.g. clouds placed at a location, or area impacts without a source entity. Forge 47 patches the vanilla
/// constructor to do exactly this, so on Forge this wrapper is a no-op; on Fabric it restores that behaviour.
@Mixin(RaycastContext.class)
public class RaycastContextNullEntity {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/ShapeContext;of(Lnet/minecraft/entity/Entity;)Lnet/minecraft/block/ShapeContext;"))
    private ShapeContext spell_engine$absentForNullEntity(Entity entity, Operation<ShapeContext> original) {
        return entity == null ? ShapeContext.absent() : original.call(entity);
    }
}
