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
        if (!(entity instanceof SpellProjectile projectile)) {
            return;
        }
        var effectiveScale = this.scale * projectile.getScaleMultiplier();
        boolean rendered = false;
        var composite = projectile.renderModels();
        if (composite != null && !composite.models.isEmpty()) {
            // New multi-model path.
            rendered = renderComposite(effectiveScale, this.dispatcher, this.itemRenderer, composite, projectile.heldItemModelId(),
                    projectile.previousVelocity, entity, tickDelta, true, matrices, vertexConsumers, light);
        } else if (projectile.renderData() != null) {
            // Legacy single-model path.
            rendered = render(effectiveScale, this.dispatcher, this.itemRenderer, projectile.renderData(), projectile.previousVelocity,
                    entity, yaw, tickDelta, true, matrices, vertexConsumers, light);
        }
        if (rendered) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    /// New multi-model path: each model positioned/oriented/spun independently and animated through
    /// the modelFX system ({@link ModelEffectOperations#applyTransforms}). Public static like the legacy
    /// `render(...)` so it can be driven both by this renderer and by ProjectileEntityRendererMixin (arrow
    /// `override_render_models`). `heldItemModelId` may be null when there is no captured held item (e.g.
    /// arrows) — models with `use_held_item` are then skipped. Returns true if rendering happened (keeps
    /// the legacy near-camera guard, so the caller knows whether to draw the debug hitbox).
    public static boolean renderComposite(float scale, EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer,
                                          Spell.ProjectileModelComposite composite, @Nullable String heldItemModelId,
                                          @Nullable Vec3d previousVelocity, Entity entity, float tickDelta, boolean allowSpin,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // Skip while very fresh and very close to the camera, so a just-spawned projectile doesn't
        // fill the caster's view (same guard as the legacy renderer).
        if (entity.age < 2 && dispatcher.camera.getFocusedEntity().squaredDistanceTo(entity) < 12.25) {
            return false;
        }

        float age = entity.age + tickDelta;
        for (var model : composite.models) {
            var fx = model.fx;
            matrices.push();

            // Placement within the entity bounding box (entity space, before the entity scale).
            if (fx.positioning != null && fx.positioning.vertical != 0) {
                matrices.translate(0, fx.positioning.vertical * entity.getHeight(), 0);
            }
            // Overall projectile scale.
            matrices.scale(scale, scale, scale);
            // Facing relative to travel.
            applyCompositeOrientation(model.orientation, dispatcher, entity, previousVelocity, tickDelta, matrices);
            // Continuous spin (disabled e.g. while an arrow is stuck in the ground).
            if (allowSpin && (model.rotate_degrees_per_tick != 0 || model.rotate_degrees_offset != 0)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                        model.rotate_degrees_offset + age * model.rotate_degrees_per_tick));
            }
            // modelFX animation (initial + animated transforms + fx.scale).
            ModelEffectOperations.applyTransforms(matrices, fx, age);

            // Draw: held-item id when requested (resolved by CustomModels' item fallback), else fx.model_id.
            var modelId = model.use_held_item ? heldItemModelId : fx.model_id;
            if (modelId != null && !modelId.isEmpty()) {
                var layer = SpellModelHelper.LAYERS.get(fx.light_emission);
                // Held items are item models authored in item-display space; apply the FIXED (item-frame)
                // display transform, as the legacy single-model path did, so their base orientation matches
                // what the orientation math (e.g. ALONG_MOTION) expects. Custom fx.model_id models render raw.
                var transformationMode = model.use_held_item ? ModelTransformationMode.FIXED : null;
                CustomModels.render(layer, itemRenderer, Identifier.of(modelId), transformationMode, matrices, vertexConsumers, light, entity.getId());
            }

            matrices.pop();
        }
        return true;
    }

    private static void applyCompositeOrientation(Spell.ProjectileModelComposite.Orientation orientation, EntityRenderDispatcher dispatcher,
                                                  Entity entity, @Nullable Vec3d previousVelocity, float tickDelta, MatrixStack matrices) {
        switch (orientation) {
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
                var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F;
                var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) directionBasedYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) directionBasedPitch));
                if (orientation == Spell.ProjectileModelComposite.Orientation.ALONG_MOTION) {
                    // ALONG_MOTION models lie along their local X axis, so folding the +90 into the
                    // yaw would leave the pitch (applied around X) rotating the model about its own
                    // length — no elevation, model stuck facing the horizon. Instead convert the X
                    // length into the +Z "forward" that the yaw/pitch above already orient, applied
                    // innermost (last) so it doesn't cancel the vertical component of the motion.
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90F));
                }
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
