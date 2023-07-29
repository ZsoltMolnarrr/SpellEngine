package net.spell_engine.mixin.client.control;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderHotbar", at = @At(value = "HEAD"), cancellable = true)
    private void renderHotbar_HEAD_SpellHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        if (!InputHelper.hotbarVisibility().item()) {
            ci.cancel();
            return;
        }

        // If rendering (not returning above), use half opacity conditionally
        if (SpellEngineClient.config.indicateActiveHotbar && InputHelper.isLocked) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.5f);
        }
    }

    @Inject(method = "renderHotbar", at = @At(value = "TAIL"))
    private void renderHotbar_TAIL_SpellHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        // Restore opacity
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
