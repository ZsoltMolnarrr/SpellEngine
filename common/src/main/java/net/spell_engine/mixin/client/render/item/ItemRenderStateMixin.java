package net.spell_engine.mixin.client.render.item;

import net.spell_engine.client.render.extension.ItemRenderStateExtension;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.spell_engine.client.render.ItemGlowRendering;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemRenderStateMixin implements ItemRenderStateExtension {
    @Shadow ItemDisplayContext displayContext;
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;
    @Shadow private int activeLayerCount;

    @Unique @Nullable private Color spellEngine_glow;

    @Override
    public void spellEngine_setGlow(@Nullable Color glow) {
        this.spellEngine_glow = glow;
    }

    @Override
    public @Nullable Color spellEngine_getGlow() {
        return spellEngine_glow;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void clear_TAIL_SpellEngine(CallbackInfo ci) {
        spellEngine_glow = null;
    }

    /// Raised before the layers read it, so the item itself is lit by its glow
    @ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int render_HEAD_SpellEngine_litItemGlow(int light) {
        var glow = spellEngine_glow;
        return glow == null ? light : ItemGlowRendering.light(glow, light);
    }

    /// The glow pass, submitted after the item's own layers so it is drawn on top of them
    @Inject(method = "submit", at = @At("TAIL"))
    private void render_TAIL_SpellEngine_itemGlow(PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outlineColor, CallbackInfo ci) {
        var glow = spellEngine_glow;
        if (glow == null) {
            return;
        }
        for (int i = 0; i < activeLayerCount; i++) {
            var layer = (LayerRenderStateAccessor) layers[i];
            if (layer.spellEngine_renderLayer() == null) {
                continue; // special model renderers (heads, shields, ...) carry no quads
            }
            matrices.pushPose();
            layer.spellEngine_transform().apply(displayContext.leftHand(), matrices.last());
            ItemGlowRendering.submitGlow(glow, layer.spellEngine_quads(), matrices, queue, light, overlay);
            matrices.popPose();
        }
    }
}
