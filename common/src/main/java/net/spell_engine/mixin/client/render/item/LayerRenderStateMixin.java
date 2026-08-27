package net.spell_engine.mixin.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.spell_engine.client.render.ItemGlowRendering;
import net.spell_engine.client.render.extension.ItemRenderStateExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The glow pass of one item layer, submitted right after the layer's own `submitItem` so it is drawn on
/// top of the item. Hooking the call site (rather than looping the layers from `ItemStackRenderState.submit`)
/// gets the pose stack exactly as vanilla positioned the layer - display transform *and* the 26.1
/// `localTransform` - and skips special-model layers (heads, shields, ...) for free: those never reach
/// `submitItem`. No private layer state is read; the quads come from the public `prepareQuadList()`.
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin {
    /// Synthetic outer-instance reference of the inner class (the glow is parked on the outer state)
    @Shadow @Final private ItemStackRenderState this$0;

    @Inject(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void submit_AfterSubmitItem_SpellEngine_itemGlow(PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outlineColor, CallbackInfo ci) {
        var glow = ((ItemRenderStateExtension) this$0).spellEngine_getGlow();
        if (glow == null) {
            return;
        }
        var quads = ((ItemStackRenderState.LayerRenderState) (Object) this).prepareQuadList();
        ItemGlowRendering.submitGlow(glow, quads, matrices, queue, light, overlay);
    }
}
