package net.spell_engine.mixin.client.render;

import net.minecraft.client.render.RenderLayer;
import net.spell_engine.api.render.CustomLayers;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/// `RenderLayer.draw` writes a hard-coded white color modulator into the dynamic uniforms
/// (`RenderSystem.setShaderColor` is gone since 1.21.6). The item glow's glint pass carries its color
/// and gain (above 1, which a vertex color cannot express) in that modulator, so it is substituted here
/// for the glow layers only.
@Mixin(RenderLayer.class)
public class RenderLayerItemGlowMixin {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/DynamicUniforms;write(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1)
    private Vector4fc draw_ModifyColorModulator_SpellEngine_itemGlow(Vector4fc colorModulator) {
        var glow = CustomLayers.itemGlowColorModulator((RenderLayer) (Object) this);
        return glow != null ? glow : colorModulator;
    }
}
