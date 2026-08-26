package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import org.jetbrains.annotations.Nullable;

// Copied from EnchantingTableBlockEntityRenderer (1.21.11: render state + command queue)
public class SpellBindingBlockEntityRenderer implements BlockEntityRenderer<SpellBindingBlockEntity, SpellBindingBlockEntityRenderer.State> {

    public static final Material BOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_binding_book")); // mapper prepends "entity/" itself

    public static class State extends BlockEntityRenderState {
        public float ticks;
        public float bookRotationDegrees;
        public float pageAngle;
        public float pageTurningSpeed;
    }

    private final MaterialSet spriteHolder;
    private final BookModel book;

    public SpellBindingBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.spriteHolder = ctx.materials();
        this.book = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpellBindingBlockEntity blockEntity, State state, float tickProgress, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
        state.pageAngle = Mth.lerp(tickProgress, blockEntity.pageAngle, blockEntity.nextPageAngle);
        state.pageTurningSpeed = Mth.lerp(tickProgress, blockEntity.pageTurningSpeed, blockEntity.nextPageTurningSpeed);
        state.ticks = blockEntity.ticks + tickProgress;
        float h = blockEntity.bookRotation - blockEntity.lastBookRotation;
        while (h >= (float) Math.PI) { h -= (float) (Math.PI * 2); }
        while (h < (float) -Math.PI) { h += (float) (Math.PI * 2); }
        state.bookRotationDegrees = blockEntity.lastBookRotation + h * tickProgress;
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        matrices.pushPose();
        matrices.translate(0.5F, 0.75F, 0.5F);
        matrices.translate(0.0F, 0.1F + Mth.sin(state.ticks * 0.1F) * 0.01F, 0.0F);
        matrices.mulPose(Axis.YP.rotation(-state.bookRotationDegrees));
        matrices.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float m = Mth.frac(state.pageAngle + 0.25F) * 1.6F - 0.3F;
        float n = Mth.frac(state.pageAngle + 0.75F) * 1.6F - 0.3F;
        var bookState = new BookModel.State(state.ticks, Mth.clamp(m, 0.0F, 1.0F), Mth.clamp(n, 0.0F, 1.0F), state.pageTurningSpeed);
        queue.submitModel(this.book, bookState, matrices, BOOK_TEXTURE.renderType(RenderTypes::entitySolid),
                state.lightCoords, OverlayTexture.NO_OVERLAY, -1, this.spriteHolder.get(BOOK_TEXTURE), 0, state.breakProgress);
        matrices.popPose();
    }
}
