package net.spell_engine.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;
import net.spell_engine.entity.SpellProjectile;


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
            if (entity instanceof FlyingSpellEntity flyingSpell) {
                switch (flyingSpell.renderMode()) {
                    case FLAT -> {
                        matrices.multiply(this.dispatcher.getRotation());
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180.0F));
                    }
                    case DEEP -> {
                        var velocity = entity.getVelocity();
                        if (((SpellProjectile)entity).previousVelocity != null) {
                            velocity = ((SpellProjectile)entity).previousVelocity.lerp(velocity, tickDelta);
                        }
                        velocity = velocity.normalize();
                        var directionBasedYaw = Math.toDegrees(Math.atan2(velocity.x, velocity.z)) + 180F; //entity.getYaw();
                        var directionBasedPitch = Math.toDegrees(Math.asin(velocity.y));
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((float) directionBasedYaw));
                        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion((float) directionBasedPitch));
                    }
                }
            }
            this.itemRenderer.renderItem(((FlyingItemEntity)entity).getStack(), ModelTransformation.Mode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getId());
            matrices.pop();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

//    private void renderItemModel() {
//        var modeld =
//        itemRenderer.models.getModel()
//
//        // We can use an empty fake ItemStack, because it is only used for checking overlay color
//
//        // #97
//        // renderBakedItemModel
//    }

    public Identifier getTexture(Entity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
