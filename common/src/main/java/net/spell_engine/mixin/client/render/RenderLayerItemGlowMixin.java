package net.spell_engine.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.spell_engine.api.render.CustomLayers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// `RenderType.prepare` (26.2; `draw` before) writes a hard-coded white color modulator into the dynamic uniforms
/// (`RenderSystem.setShaderColor` is gone since 1.21.6): `writeDynamicTransforms` calls the
/// `writeTransform(modelView, textureMatrix)` overload, which fills in `WHITE`. The item glow's glint pass carries
/// its color and gain (above 1, which a vertex color cannot express) in that modulator, so the call is rerouted to
/// the 4-arg overload with the glow color for the glow layers only.
@Mixin(RenderType.class)
public class RenderLayerItemGlowMixin {
    @WrapOperation(method = "writeDynamicTransforms", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice writeDynamicTransforms_WrapColorModulator_SpellEngine_itemGlow(DynamicUniforms uniforms, Matrix4f modelView, Matrix4f textureMatrix, Operation<GpuBufferSlice> original) {
        var glow = CustomLayers.itemGlowColorModulator((RenderType) (Object) this);
        if (glow == null) {
            return original.call(uniforms, modelView, textureMatrix);
        }
        // Same as the 2-arg overload (no model offset), with the glow color in place of WHITE
        return uniforms.writeTransform(modelView, new Vector4f(glow), new Vector3f(), textureMatrix);
    }
}
