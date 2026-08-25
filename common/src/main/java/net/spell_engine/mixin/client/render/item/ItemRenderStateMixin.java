package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
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

@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements ItemRenderStateExtension {
    @Shadow ItemDisplayContext displayContext;
    @Shadow private ItemRenderState.LayerRenderState[] layers;
    @Shadow private int layerCount;

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
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int render_HEAD_SpellEngine_litItemGlow(int light) {
        var glow = spellEngine_glow;
        return glow == null ? light : ItemGlowRendering.light(glow, light);
    }

    /// The glow pass, submitted after the item's own layers so it is drawn on top of them
    @Inject(method = "render", at = @At("TAIL"))
    private void render_TAIL_SpellEngine_itemGlow(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outlineColor, CallbackInfo ci) {
        var glow = spellEngine_glow;
        if (glow == null) {
            return;
        }
        for (int i = 0; i < layerCount; i++) {
            var layer = (LayerRenderStateAccessor) layers[i];
            if (layer.spellEngine_renderLayer() == null) {
                continue; // special model renderers (heads, shields, ...) carry no quads
            }
            matrices.push();
            layer.spellEngine_transform().apply(displayContext.isLeftHand(), matrices.peek());
            ItemGlowRendering.submitGlow(glow, layer.spellEngine_quads(), matrices, queue, light, overlay);
            matrices.pop();
        }
    }
}
