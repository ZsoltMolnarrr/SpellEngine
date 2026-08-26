package net.spell_engine.mixin.client;

import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.spell_engine.internals.SpellParameters;

@Mixin(EntityRenderer.class)
public class EntityRenderMixin {
    @Inject(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getBoundingBoxForCulling(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/AABB;"), cancellable = true)
    public void shouldRender_WhileBeaming(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof SpellCaster.Player caster) {
            var spell = caster.getCastedSpell();
            if (spell != null && SpellParameters.isChanneled(spell)) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}
