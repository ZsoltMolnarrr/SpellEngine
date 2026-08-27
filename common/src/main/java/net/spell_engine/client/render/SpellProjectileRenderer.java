package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import org.jetbrains.annotations.Nullable;

// Mostly copied from: FlyingItemEntityRenderer (1.21.11: render states + render command queue)
public class SpellProjectileRenderer<T extends Entity & ItemSupplier> extends EntityRenderer<T, SpellProjectileRenderer.State> {

    public static class State extends EntityRenderState {
        @Nullable public SpellProjectile projectile;
        public float tickDelta;
        /// The captured held item (models with `use_held_item`), resolved through the item model path
        public final ItemStackRenderState heldItem = new ItemStackRenderState();
        public boolean hasHeldItem;
    }

    private final ItemModelResolver itemModelManager;
    private final float scale;
    private final boolean lit;

    public SpellProjectileRenderer(EntityRendererProvider.Context ctx, float scale, boolean lit) {
        super(ctx);
        this.itemModelManager = ctx.getItemModelResolver();
        this.scale = scale;
        this.lit = lit;
    }

    public SpellProjectileRenderer(EntityRendererProvider.Context arg) {
        this(arg, 1.0F, false);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return this.lit ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.projectile = entity instanceof SpellProjectile projectile ? projectile : null;
        state.tickDelta = tickDelta;
        state.hasHeldItem = false;
        state.heldItem.clear();
        if (state.projectile != null) {
            var heldItemModelId = state.projectile.heldItemModelId();
            if (heldItemModelId != null && !heldItemModelId.isEmpty()) {
                var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(heldItemModelId)).orElse(null);
                if (item != null) {
                    var stack = item.getDefaultInstance();
                    if (!stack.isEmpty()) {
                        // Item models are authored in item-display space; FIXED (item-frame) gives them the
                        // base orientation the projectile orientation math (e.g. ALONG_MOTION) expects.
                        itemModelManager.updateForNonLiving(state.heldItem, stack, ItemDisplayContext.FIXED, entity);
                        state.hasHeldItem = true;
                    }
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        var projectile = state.projectile;
        if (projectile == null) {
            return;
        }
        var effectiveScale = this.scale * projectile.getScaleMultiplier();
        boolean rendered = false;
        var composite = projectile.renderModels();
        if (composite != null && !composite.models.isEmpty()) {
            rendered = renderComposite(effectiveScale, cameraState, composite, state.hasHeldItem ? state.heldItem : null,
                    projectile.previousVelocity, projectile, state.tickDelta, true, matrices, queue, state.lightCoords);
        }
        if (rendered) {
            super.submit(state, matrices, queue, cameraState);
        }
    }

    /// Each model positioned/oriented/spun independently and animated through the modelFX system
    /// ({@link ModelEffectOperations#applyTransforms}). Static so it can be driven both by this renderer
    /// and by ProjectileEntityRendererMixin (arrows carrying `arrow_perks.composite_model`).
    /// `heldItem` may be null when there is no captured held item (e.g. arrows) — models with
    /// `use_held_item` are then skipped. Returns true if rendering happened (the near-camera guard can
    /// skip it), so the caller knows whether to draw the debug hitbox.
    public static boolean renderComposite(float scale, CameraRenderState cameraState,
                                          Spell.ProjectileModelComposite composite, @Nullable ItemStackRenderState heldItem,
                                          @Nullable Vec3 previousVelocity, Entity entity, float tickDelta, boolean allowSpin,
                                          PoseStack matrices, SubmitNodeCollector queue, int light) {
        // Skip while very fresh and very close to the camera, so a just-spawned projectile doesn't
        // fill the caster's view.
        if (entity.tickCount < 2 && cameraState.pos.distanceToSqr(entity.position()) < 12.25) {
            return false;
        }

        float age = entity.tickCount + tickDelta;
        for (var model : composite.models) {
            var fx = model.fx;
            matrices.pushPose();

            // Placement within the entity bounding box (entity space, before the entity scale).
            if (fx.positioning != null && fx.positioning.vertical != 0) {
                matrices.translate(0, fx.positioning.vertical * entity.getBbHeight(), 0);
            }
            // Overall projectile scale.
            matrices.scale(scale, scale, scale);
            // Facing relative to travel.
            applyCompositeOrientation(model.orientation, cameraState, entity, previousVelocity, tickDelta, matrices);
            // Continuous spin (disabled e.g. while an arrow is stuck in the ground).
            if (allowSpin && (model.rotate_degrees_per_tick != 0 || model.rotate_degrees_offset != 0)) {
                matrices.mulPose(Axis.ZP.rotationDegrees(
                        model.rotate_degrees_offset + age * model.rotate_degrees_per_tick));
            }
            // modelFX animation (initial + animated transforms + fx.scale).
            ModelEffectOperations.applyTransforms(matrices, fx, age);

            var layer = SpellModelHelper.LAYERS.get(fx.light_emission);
            if (model.use_held_item) {
                // Held items render through the item model path (the item's own layer; the fx light emission
                // is not applied to them since 1.21.4 item models pick their layers themselves)
                if (heldItem != null) {
                    heldItem.submit(matrices, queue, light, OverlayTexture.NO_OVERLAY, 0);
                }
            } else if (fx.model_id != null && !fx.model_id.isEmpty()) {
                // Custom (non-item) fx models render raw, with no display transform.
                CustomModels.render(layer, Identifier.parse(fx.model_id), matrices, queue, light, entity.getId());
            }

            matrices.popPose();
        }
        return true;
    }

    private static void applyCompositeOrientation(Spell.ProjectileModelComposite.Orientation orientation, CameraRenderState cameraState,
                                                  Entity entity, @Nullable Vec3 previousVelocity, float tickDelta, PoseStack matrices) {
        switch (orientation) {
            case TOWARDS_CAMERA -> {
                matrices.mulPose(cameraState.orientation);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case TOWARDS_MOTION, ALONG_MOTION -> {
                var velocity = entity.getDeltaMovement();
                if (previousVelocity != null) {
                    velocity = previousVelocity.lerp(velocity, tickDelta);
                }
                velocity = velocity.normalize();
                var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F;
                var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                matrices.mulPose(Axis.YP.rotationDegrees((float) directionBasedYaw));
                matrices.mulPose(Axis.XP.rotationDegrees((float) directionBasedPitch));
                if (orientation == Spell.ProjectileModelComposite.Orientation.ALONG_MOTION) {
                    // ALONG_MOTION models lie along their local X axis; convert the X length into the +Z
                    // "forward" that the yaw/pitch above already orient, applied innermost (last).
                    matrices.mulPose(Axis.YP.rotationDegrees(90F));
                }
            }
        }
    }
}
