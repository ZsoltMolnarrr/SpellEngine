package net.spell_engine.mixin.client.render.state;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererStateMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void updateRenderState_TAIL_SpellEngine(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((EntityRenderStateExtension) state).spellEngine_setEntity(entity, tickDelta);
    }
}
