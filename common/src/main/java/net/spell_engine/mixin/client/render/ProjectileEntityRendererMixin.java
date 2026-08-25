package net.spell_engine.mixin.client.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.render.entity.state.ProjectileEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.internals.delivery.arrow.ArrowExtension;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntityRenderer.class)
public abstract class ProjectileEntityRendererMixin extends EntityRenderer<PersistentProjectileEntity, ProjectileEntityRenderState> {
    protected ProjectileEntityRendererMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ProjectileEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void render_HEAD_SpellEngine(ProjectileEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
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
                            Vec3d.ZERO, entity, tickDelta, allowSpin, matrices, queue, state.light);
                    if (rendered) {
                        super.render(state, matrices, queue, cameraState);
                    }
                    return;
                }
            }
        }
    }
}
