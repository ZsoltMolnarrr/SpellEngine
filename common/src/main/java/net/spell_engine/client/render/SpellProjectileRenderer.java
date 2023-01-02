package net.spell_engine.client.render;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.registry.Registry;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.mixin.client.render.ItemRendererAccessor;


// Mostly copied from: FlyingItemEntityRenderer
public class SpellProjectileRenderer<T extends Entity & FlyingItemEntity> extends EntityRenderer<T> {
    private final ItemRenderer itemRenderer;
    private final float scale;
    private final boolean lit;

    public SpellProjectileRenderer(EntityRendererFactory.Context ctx, float scale, boolean lit) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
        this.lit = lit;
    }

    public SpellProjectileRenderer(EntityRendererFactory.Context arg) {
        this(arg, 1.0F, false);
    }

    protected int getBlockLight(T entity, BlockPos pos) {
        return this.lit ? 15 : super.getBlockLight(entity, pos);
    }

    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity.age >= 2 || !(this.dispatcher.camera.getFocusedEntity().squaredDistanceTo(entity) < 12.25)) {
            matrices.push();
            matrices.scale(this.scale, this.scale, this.scale);
            if (entity instanceof SpellProjectile projectile && projectile.renderData() != null) {
                var renderData = projectile.renderData();
                switch (renderData.render) {
                    case FLAT -> {
                        matrices.multiply(this.dispatcher.getRotation());
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180.0F));
                    }
                    case DEEP -> {
                        var velocity = entity.getVelocity();
                        if (projectile.previousVelocity != null) {
                            velocity = projectile.previousVelocity.lerp(velocity, tickDelta);
                        }
                        velocity = velocity.normalize();
                        var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F; //entity.getYaw();
                        var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((float) directionBasedYaw));
                        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion((float) directionBasedPitch));
                    }
                }

                var time = entity.world.getTime();
                var absoluteTime = (float)time + tickDelta;
                matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(absoluteTime * renderData.rotate_degrees_per_tick));
                matrices.scale(renderData.scale, renderData.scale, renderData.scale);
                if (renderData.model_id != null && !renderData.model_id.isEmpty()) {
                    var modelId = new Identifier(renderData.model_id);
                    render(modelId, matrices, vertexConsumers, light, entity.getId());
                }
            }
            matrices.pop();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    public static final RenderLayer LAYER = CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    private void render(Identifier id, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int seed) {
        var model = ProjectileModels.getModel(id);
        if (model == null) {
            var stack = Registry.ITEM.get(id).getDefaultStack();
            if (!stack.isEmpty()) {
                model = itemRenderer.getModel(stack, null, null, seed);
            }
        }
        var buffer = vertexConsumers.getBuffer(LAYER);
        matrices.translate(-0.5, -0.5, -0.5);
        ((ItemRendererAccessor)itemRenderer).renderBakedItemModel(model, ItemStack.EMPTY, light, OverlayTexture.DEFAULT_UV, matrices, buffer);
    }

    public Identifier getTexture(Entity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
