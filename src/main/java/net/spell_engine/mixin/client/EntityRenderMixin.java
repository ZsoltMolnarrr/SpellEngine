package net.spell_engine.mixin.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRenderMixin {
    @Inject(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getVisibilityBoundingBox()Lnet/minecraft/util/math/Box;"), cancellable = true)
    public void shouldRender_WhileBeaming(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof SpellCasterEntity caster) {
            if (caster.isBeaming()) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}
