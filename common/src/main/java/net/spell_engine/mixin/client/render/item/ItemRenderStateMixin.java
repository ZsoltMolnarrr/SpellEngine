package net.spell_engine.mixin.client.render.item;

import net.spell_engine.client.render.extension.ItemRenderStateExtension;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.spell_engine.client.render.ItemGlowRendering;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Parks the glow the holder casts on the item (resolved in `ItemModelManagerMixin`) on the render state,
/// and lights the item by it. The glow pass itself is submitted per layer, see `LayerRenderStateMixin`.
@Mixin(ItemStackRenderState.class)
public abstract class ItemRenderStateMixin implements ItemRenderStateExtension {
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
    private int submit_HEAD_SpellEngine_litItemGlow(int light) {
        var glow = spellEngine_glow;
        return glow == null ? light : ItemGlowRendering.light(glow, light);
    }
}
