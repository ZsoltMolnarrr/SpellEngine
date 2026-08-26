package net.spell_engine.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.internals.delivery.arrow.ArrowExtension;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrowRenderer.class)
public abstract class ProjectileEntityRendererMixin extends EntityRenderer<AbstractArrow, ArrowRenderState> {
    protected ProjectileEntityRendererMixin(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ArrowRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void render_HEAD_SpellEngine(ArrowRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
        var entity = ((EntityRenderStateExtension) state).spellEngine_getEntity();
        if (entity instanceof ArrowExtension arrowExtension) {
            var tickDelta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
            for (var spellEntry: arrowExtension.getCarriedSpells()) {
                var arrowPerks = spellEntry.value().arrow_perks;
                if (arrowPerks == null) {
                    continue;
                }
                var allowSpin = !arrowExtension.isInGround_SpellEngine();
                var composite = arrowPerks.composite_model;
                if (composite != null && !composite.models.isEmpty()) {
                    ci.cancel();
                    // Arrows have no captured held item, so models with use_held_item are skipped (null).
                    var rendered = SpellProjectileRenderer.renderComposite(1F, cameraState, composite, null,
                            Vec3.ZERO, entity, tickDelta, allowSpin, matrices, queue, state.lightCoords);
                    if (rendered) {
                        super.submit(state, matrices, queue, cameraState);
                    }
                    return;
                }
            }
        }
    }
}
