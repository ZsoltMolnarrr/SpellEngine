package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.mixin.client.render.ItemRendererAccessor;
import org.jetbrains.annotations.Nullable;


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
                    entity, yaw, tickDelta, true, matrices, vertexConsumers, light);
            if (rendered) {
                super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            }
        }
    }

    public static boolean render(float scale, EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer, Spell.ProjectileModel renderData,
                                 @Nullable Vec3d previousVelocity, Entity entity, float yaw, float tickDelta, boolean allowSpin,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity.age >= 2 || !(dispatcher.camera.getFocusedEntity().squaredDistanceTo(entity) < 12.25)) {
            matrices.push();
            matrices.scale(scale, scale, scale);
            switch (renderData.orientation) {
                case TOWARDS_CAMERA -> {
                    matrices.multiply(dispatcher.getRotation());
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                }
                case TOWARDS_MOTION, ALONG_MOTION -> {
                    var velocity = entity.getVelocity();
                    if (previousVelocity != null) {
                        velocity = previousVelocity.lerp(velocity, tickDelta);
                    }
                    velocity = velocity.normalize();
                    var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F; //entity.getYaw();
                    if (renderData.orientation == Spell.ProjectileModel.Orientation.ALONG_MOTION) {
                        directionBasedYaw += 90;
                    }
                    var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) directionBasedYaw));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) directionBasedPitch));
                }
            }

            if (allowSpin) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                        renderData.rotate_degrees_offset +
                        (entity.age + tickDelta) * renderData.rotate_degrees_per_tick)
                );
            }
            matrices.scale(renderData.scale, renderData.scale, renderData.scale);

            Identifier modelId = null;
            ItemStack modelItemStack = null;
            if (entity instanceof SpellProjectile spellProjectile && spellProjectile.getItemStackModel() != null) {
                modelItemStack = spellProjectile.getItemStackModel();
            } else if (renderData.model_id != null && !renderData.model_id.isEmpty()) {
                modelId = Identifier.of(renderData.model_id);
            }

            var layer = SpellModelHelper.LAYERS.get(renderData.light_emission);
            if (modelItemStack != null) {
                var model = itemRenderer.getModel(modelItemStack, entity.getWorld(), null, entity.getId());
                model.getTransformation().getTransformation(ModelTransformationMode.FIXED).apply(false, matrices);
                CustomModels.renderModel(layer, (ItemRendererAccessor) itemRenderer, matrices, vertexConsumers, light, model);
            } else if (modelId != null) {
                CustomModels.render(layer, itemRenderer, modelId, matrices, vertexConsumers, light, entity.getId());
            }
            matrices.pop();
            return true;
        }
        return false;
    }

    public Identifier getTexture(Entity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
