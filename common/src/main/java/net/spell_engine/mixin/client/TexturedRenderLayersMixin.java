package net.spell_engine.mixin.client;

import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.spell_engine.client.render.SpellBindingBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(TexturedRenderLayers.class)
public class TexturedRenderLayersMixin {
    @Inject(method = "addDefaultTextures", at = @At("HEAD"))
    private static void addDefaultTextures_HEAD_SpellEngine(Consumer<SpriteIdentifier> adder, CallbackInfo ci) {
        adder.accept(SpellBindingBlockEntityRenderer.BOOK_TEXTURE);
    }
}
