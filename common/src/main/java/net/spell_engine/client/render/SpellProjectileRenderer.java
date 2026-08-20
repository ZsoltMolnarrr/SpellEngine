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
import net.minecraft.registry.Registries;
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
            rendered = renderComposite(effectiveScale, this.dispatcher, this.itemRenderer, composite, projectile.heldItemModelId(),
                    projectile.previousVelocity, entity, tickDelta, true, matrices, vertexConsumers, light);
        }
        if (rendered) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    /// Each model positioned/oriented/spun independently and animated through the modelFX system
    /// ({@link ModelEffectOperations#applyTransforms}). Static so it can be driven both by this renderer
    /// and by ProjectileEntityRendererMixin (arrows carrying `arrow_perks.composite_model`).
    /// `heldItemModelId` may be null when there is no captured held item (e.g. arrows) — models with
    /// `use_held_item` are then skipped. Returns true if rendering happened (the near-camera guard can
    /// skip it), so the caller knows whether to draw the debug hitbox.
    public static boolean renderComposite(float scale, EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer,
                                          Spell.ProjectileModelComposite composite, @Nullable String heldItemModelId,
                                          @Nullable Vec3d previousVelocity, Entity entity, float tickDelta, boolean allowSpin,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // Skip while very fresh and very close to the camera, so a just-spawned projectile doesn't
        // fill the caster's view.
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

            var layer = SpellModelHelper.LAYERS.get(fx.light_emission);
            if (model.use_held_item) {
                // Held items MUST resolve through the item model path (itemRenderer.getModel(stack, ...)).
                // Resolving them as a raw model Identifier via CustomModels.render breaks on NeoForge: getModel returns the missing-model placeholder
                // (never null) for a bare item id — an item's model isn't registered under the `#standalone`
                // variant the NeoForge branch looks up — so the item-stack fallback never fires and an
                // empty/placeholder model is drawn. heldItemModelId is null for non-held projectiles (e.g.
                // arrows via ProjectileEntityRendererMixin), which then correctly skip these models.
                if (heldItemModelId != null && !heldItemModelId.isEmpty()) {
                    var stack = Registries.ITEM.get(Identifier.of(heldItemModelId)).getDefaultStack();
                    if (!stack.isEmpty()) {
                        var itemModel = itemRenderer.getModel(stack, entity.getWorld(), null, entity.getId());
                        // Item models are authored in item-display space; FIXED (item-frame) gives them the
                        // base orientation the projectile orientation math (e.g. ALONG_MOTION) expects.
                        itemModel.getTransformation().getTransformation(ModelTransformationMode.FIXED).apply(false, matrices);
                        CustomModels.renderModel(layer, (ItemRendererAccessor) itemRenderer, matrices, vertexConsumers, light, itemModel);
                    }
                }
            } else if (fx.model_id != null && !fx.model_id.isEmpty()) {
                // Custom (non-item) fx models render raw, with no display transform.
                CustomModels.render(layer, itemRenderer, Identifier.of(fx.model_id), null, matrices, vertexConsumers, light, entity.getId());
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

    public Identifier getTexture(Entity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
