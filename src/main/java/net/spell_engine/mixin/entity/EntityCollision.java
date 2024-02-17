package net.spell_engine.mixin.entity;

import net.minecraft.entity.Entity;
import net.spell_engine.api.entity.TwoWayCollisionChecker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Entity.class)
public class EntityCollision implements TwoWayCollisionChecker {
    private Function<Entity, CollisionResult> reverseCollisionChecker;

    @Override
    public @Nullable Function<Entity, CollisionResult> getReverseCollisionChecker() {
        return reverseCollisionChecker;
    }

    @Override
    public void setReverseCollisionChecker(Function<Entity, CollisionResult> reverseCollisionChecker) {
        this.reverseCollisionChecker = reverseCollisionChecker;
    }

    @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
    private void onCollidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        var reverse = ((TwoWayCollisionChecker) other).getReverseCollisionChecker();
        if (reverse != null) {
            var result = reverse.apply((Entity) (Object) this);
            switch (result) {
                case COLLIDE -> {
                    cir.setReturnValue(true);
                    cir.cancel();
                }
                case PASS -> {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            }
        }
    }
}
