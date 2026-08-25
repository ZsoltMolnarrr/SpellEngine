package net.spell_engine.client.render;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import org.jetbrains.annotations.Nullable;

// Mostly copied from: FlyingItemEntityRenderer (1.21.11: render states + render command queue)
public class SpellProjectileRenderer<T extends Entity & FlyingItemEntity> extends EntityRenderer<T, SpellProjectileRenderer.State> {

    public static class State extends EntityRenderState {
        @Nullable public SpellProjectile projectile;
        public float tickDelta;
        /// The captured held item (models with `use_held_item`), resolved through the item model path
        public final ItemRenderState heldItem = new ItemRenderState();
        public boolean hasHeldItem;
    }

    private final ItemModelManager itemModelManager;
    private final float scale;
    private final boolean lit;

    public SpellProjectileRenderer(EntityRendererFactory.Context ctx, float scale, boolean lit) {
        super(ctx);
        this.itemModelManager = ctx.getItemModelManager();
        this.scale = scale;
        this.lit = lit;
    }

    public SpellProjectileRenderer(EntityRendererFactory.Context arg) {
        this(arg, 1.0F, false);
    }

    @Override
    protected int getBlockLight(T entity, BlockPos pos) {
        return this.lit ? 15 : super.getBlockLight(entity, pos);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(T entity, State state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.projectile = entity instanceof SpellProjectile projectile ? projectile : null;
        state.tickDelta = tickDelta;
        state.hasHeldItem = false;
        state.heldItem.clear();
        if (state.projectile != null) {
            var heldItemModelId = state.projectile.heldItemModelId();
            if (heldItemModelId != null && !heldItemModelId.isEmpty()) {
                var item = Registries.ITEM.getOptionalValue(Identifier.of(heldItemModelId)).orElse(null);
                if (item != null) {
                    var stack = item.getDefaultStack();
                    if (!stack.isEmpty()) {
                        // Item models are authored in item-display space; FIXED (item-frame) gives them the
                        // base orientation the projectile orientation math (e.g. ALONG_MOTION) expects.
                        itemModelManager.updateForNonLivingEntity(state.heldItem, stack, ItemDisplayContext.FIXED, entity);
                        state.hasHeldItem = true;
                    }
                }
            }
        }
    }

    @Override
    public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        var projectile = state.projectile;
        if (projectile == null) {
            return;
        }
        var effectiveScale = this.scale * projectile.getScaleMultiplier();
        boolean rendered = false;
        var composite = projectile.renderModels();
        if (composite != null && !composite.models.isEmpty()) {
            rendered = renderComposite(effectiveScale, cameraState, composite, state.hasHeldItem ? state.heldItem : null,
                    projectile.previousVelocity, projectile, state.tickDelta, true, matrices, queue, state.light);
        }
        if (rendered) {
            super.render(state, matrices, queue, cameraState);
        }
    }

    /// Each model positioned/oriented/spun independently and animated through the modelFX system
    /// ({@link ModelEffectOperations#applyTransforms}). Static so it can be driven both by this renderer
    /// and by ProjectileEntityRendererMixin (arrows carrying `arrow_perks.composite_model`).
    /// `heldItem` may be null when there is no captured held item (e.g. arrows) — models with
    /// `use_held_item` are then skipped. Returns true if rendering happened (the near-camera guard can
    /// skip it), so the caller knows whether to draw the debug hitbox.
    public static boolean renderComposite(float scale, CameraRenderState cameraState,
                                          Spell.ProjectileModelComposite composite, @Nullable ItemRenderState heldItem,
                                          @Nullable Vec3d previousVelocity, Entity entity, float tickDelta, boolean allowSpin,
                                          MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        // Skip while very fresh and very close to the camera, so a just-spawned projectile doesn't
        // fill the caster's view.
        if (entity.age < 2 && cameraState.pos.squaredDistanceTo(entity.getEntityPos()) < 12.25) {
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
            applyCompositeOrientation(model.orientation, cameraState, entity, previousVelocity, tickDelta, matrices);
            // Continuous spin (disabled e.g. while an arrow is stuck in the ground).
            if (allowSpin && (model.rotate_degrees_per_tick != 0 || model.rotate_degrees_offset != 0)) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                        model.rotate_degrees_offset + age * model.rotate_degrees_per_tick));
            }
            // modelFX animation (initial + animated transforms + fx.scale).
            ModelEffectOperations.applyTransforms(matrices, fx, age);

            var layer = SpellModelHelper.LAYERS.get(fx.light_emission);
            if (model.use_held_item) {
                // Held items render through the item model path (the item's own layer; the fx light emission
                // is not applied to them since 1.21.4 item models pick their layers themselves)
                if (heldItem != null) {
                    heldItem.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
                }
            } else if (fx.model_id != null && !fx.model_id.isEmpty()) {
                // Custom (non-item) fx models render raw, with no display transform.
                CustomModels.render(layer, Identifier.of(fx.model_id), matrices, queue, light, entity.getId());
            }

            matrices.pop();
        }
        return true;
    }

    private static void applyCompositeOrientation(Spell.ProjectileModelComposite.Orientation orientation, CameraRenderState cameraState,
                                                  Entity entity, @Nullable Vec3d previousVelocity, float tickDelta, MatrixStack matrices) {
        switch (orientation) {
            case TOWARDS_CAMERA -> {
                matrices.multiply(cameraState.orientation);
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
                    // ALONG_MOTION models lie along their local X axis; convert the X length into the +Z
                    // "forward" that the yaw/pitch above already orient, applied innermost (last).
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90F));
                }
            }
        }
    }
}
