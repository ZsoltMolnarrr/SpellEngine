package net.spell_engine.mixin.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.internals.arrow.ArrowExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntityRenderer.class)
public abstract class ProjectileEntityRendererMixin extends EntityRenderer {
    protected ProjectileEntityRendererMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    private ItemRenderer itemRenderer;
    // Inject to init tail
    @Inject(method = "<init>", at = @At("TAIL"))
    private void init_TAIL_SpellEngine(EntityRendererFactory.Context context, CallbackInfo ci) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Inject(
            method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void render_HEAD_SpellEngine(Entity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (entity instanceof ArrowExtension arrowExtension) {
            if (arrowExtension.getCarriedSpell() != null) {
                var arrowPerks = arrowExtension.getCarriedSpell().arrow_perks;
                if (arrowPerks != null) {
                    var renderData = arrowPerks.override_render;
                    if (renderData != null) {
                        ci.cancel();
                        var allowSpin = !arrowExtension.isInGround_SpellEngine();
                        var rendered = SpellProjectileRenderer.render(1F, this.dispatcher, this.itemRenderer, renderData, Vec3d.ZERO,
                                entity, yaw, tickDelta, allowSpin, matrices, vertexConsumers, light);
                        ci.cancel();
                        if (rendered) {
                            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
                        }
                    }
                }
            }
        }
    }
}
