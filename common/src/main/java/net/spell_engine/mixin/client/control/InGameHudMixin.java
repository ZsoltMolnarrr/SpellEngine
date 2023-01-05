package net.spell_engine.mixin.client.control;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private void renderHotbar_HEAD_SpellHotbar(float tickDelta, MatrixStack matrices, CallbackInfo ci) {
        if (!InputHelper.hotbarVisibility().item()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", shift = At.Shift.AFTER))
    private void renderHotbar_AFTER_setShaderColor_SpellHotbar(float tickDelta, MatrixStack matrices, CallbackInfo ci) {
        if (SpellEngineClient.config.indicateActiveHotbar && InputHelper.isLocked) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.5f);
        }
    }
}
