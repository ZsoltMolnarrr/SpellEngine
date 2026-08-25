package net.spell_engine.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BookModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.SpriteHolder;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import org.jetbrains.annotations.Nullable;

// Copied from EnchantingTableBlockEntityRenderer (1.21.11: render state + command queue)
public class SpellBindingBlockEntityRenderer implements BlockEntityRenderer<SpellBindingBlockEntity, SpellBindingBlockEntityRenderer.State> {

    public static final SpriteIdentifier BOOK_TEXTURE = TexturedRenderLayers.ENTITY_SPRITE_MAPPER.map(Identifier.of(SpellEngineMod.ID, "entity/spell_binding_book"));

    public static class State extends BlockEntityRenderState {
        public float ticks;
        public float bookRotationDegrees;
        public float pageAngle;
        public float pageTurningSpeed;
    }

    private final SpriteHolder spriteHolder;
    private final BookModel book;

    public SpellBindingBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.spriteHolder = ctx.spriteHolder();
        this.book = new BookModel(ctx.getLayerModelPart(EntityModelLayers.BOOK));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(SpellBindingBlockEntity blockEntity, State state, float tickProgress, Vec3d cameraPos, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
        state.pageAngle = MathHelper.lerp(tickProgress, blockEntity.pageAngle, blockEntity.nextPageAngle);
        state.pageTurningSpeed = MathHelper.lerp(tickProgress, blockEntity.pageTurningSpeed, blockEntity.nextPageTurningSpeed);
        state.ticks = blockEntity.ticks + tickProgress;
        float h = blockEntity.bookRotation - blockEntity.lastBookRotation;
        while (h >= (float) Math.PI) { h -= (float) (Math.PI * 2); }
        while (h < (float) -Math.PI) { h += (float) (Math.PI * 2); }
        state.bookRotationDegrees = blockEntity.lastBookRotation + h * tickProgress;
    }

    @Override
    public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        matrices.push();
        matrices.translate(0.5F, 0.75F, 0.5F);
        matrices.translate(0.0F, 0.1F + MathHelper.sin(state.ticks * 0.1F) * 0.01F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-state.bookRotationDegrees));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(80.0F));
        float m = MathHelper.fractionalPart(state.pageAngle + 0.25F) * 1.6F - 0.3F;
        float n = MathHelper.fractionalPart(state.pageAngle + 0.75F) * 1.6F - 0.3F;
        var bookState = new BookModel.BookModelState(state.ticks, MathHelper.clamp(m, 0.0F, 1.0F), MathHelper.clamp(n, 0.0F, 1.0F), state.pageTurningSpeed);
        queue.submitModel(this.book, bookState, matrices, BOOK_TEXTURE.getRenderLayer(RenderLayers::entitySolid),
                state.lightmapCoordinates, OverlayTexture.DEFAULT_UV, -1, this.spriteHolder.getSprite(BOOK_TEXTURE), 0, state.crumblingOverlay);
        matrices.pop();
    }
}
