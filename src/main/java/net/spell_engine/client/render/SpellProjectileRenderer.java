package net.spell_engine.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.render.LightEmission;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


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
        if (entity instanceof SpellProjectile projectile && projectile.renderData() != null) {
            var renderData = projectile.renderData();
            var rendered = render(this.scale, this.dispatcher, this.itemRenderer, renderData, projectile.previousVelocity,
                    entity, yaw, tickDelta, matrices, vertexConsumers, light);
            if (rendered) {
                super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            }
        }
    }

    public static boolean render(float scale, EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer, Spell.ProjectileData.Client renderData, @Nullable Vec3d previousVelocity,
                              Entity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity.age >= 2 || !(dispatcher.camera.getFocusedEntity().squaredDistanceTo(entity) < 12.25)) {
            matrices.push();
            matrices.scale(scale, scale, scale);
            switch (renderData.render) {
                case FLAT -> {
                    matrices.multiply(dispatcher.getRotation());
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                }
                case DEEP -> {
                    var velocity = entity.getVelocity();
                    if (previousVelocity != null) {
                        velocity = previousVelocity.lerp(velocity, tickDelta);
                    }
                    velocity = velocity.normalize();
                    var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F; //entity.getYaw();
                    var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) directionBasedYaw));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) directionBasedPitch));
                }
            }

            var time = entity.getWorld().getTime();
            var absoluteTime = (float)time + tickDelta;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(absoluteTime * renderData.rotate_degrees_per_tick));
            matrices.scale(renderData.scale, renderData.scale, renderData.scale);
            if (renderData.model_id != null && !renderData.model_id.isEmpty()) {
                var modelId = new Identifier(renderData.model_id);
                CustomModels.render(LAYERS.get(renderData.light_emission), itemRenderer, modelId, matrices, vertexConsumers, light, entity.getId());
            }
            matrices.pop();
            return true;
        }
        return false;
    }

    private static final Map<LightEmission, RenderLayer> LAYERS = Map.of(
            LightEmission.NONE, CustomLayers.projectile(LightEmission.NONE),
            LightEmission.GLOW, CustomLayers.projectile(LightEmission.GLOW),
            LightEmission.RADIATE, CustomLayers.projectile(LightEmission.RADIATE)
    );

    public Identifier getTexture(Entity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
