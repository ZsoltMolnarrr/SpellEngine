package net.spell_engine.mixin.client.render.tint;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.api.effect.EntityTints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/// Entity tint at the model-part level (see [EntityTints.Current]).
///
/// Targets `render`, not the per-part `renderCuboids` leaf the 1.21 line hooks, because on 1.20.1
/// `renderCuboids` is not a shared bottleneck: **Sodium 0.5.x cancels `ModelPart#render` outright**
/// (`features.render.entity.ModelPartMixin#onRender` swaps in its own `EntityRenderer`, packing the
/// four color floats it was handed and never calling `renderCuboids`), and Iris 1.7.x force-disables
/// that Sodium mixin only to apply its own byte-identical copy of it
/// (`compat.sodium.mixin.copyEntity.ModelPartMixin`). With either installed the leaf hook is dead
/// code and no entity ever tints. The modern Sodium the 1.21 line runs against moved this hook one
/// level down, onto the per-cuboid compile inside `ModelPart.Cuboid`, which is *below*
/// `renderCuboids` — which is why the leaf hook still works there and only this line needs the change.
///
/// `render` is the one place both paths agree on: vanilla forwards its color arguments into
/// `renderCuboids`, Sodium/Iris read those same arguments off the stack. Tinting them therefore
/// covers the fast path and the slow path with a single hook, and covers armor, elytra and every
/// other feature-renderer model too, since they all draw through `ModelPart#render` as well.
///
/// Two consequences of moving up from the leaf, both handled here:
///
/// - `render` recurses into its children, passing the color along, so a naive multiply would compound
///   the tint once per hierarchy level. [#spellEngine_tintDepth] applies it on the outermost call only;
///   children inherit the already-tinted color. (Sodium's path cancels before recursing and draws the
///   whole hierarchy itself, so it only ever sees the outermost call.)
/// - The mixin has to be applied *after* Sodium's and Iris's, or their `@At("HEAD")` injection would
///   read the arguments before this wrapper had a chance to change them. Both configure the default
///   priority of 1000, hence 1500 here.
///
/// 1.20.1 carries the color as four floats rather than the packed ARGB int of 1.21, so the multiply is
/// applied per channel. The channels come from the same ARGB [EntityTints.Current] holds; multiplying a
/// channel by `tint / 255` is exactly what the packed-int `EntityTints.multiply` did on 1.21.
@Mixin(value = ModelPart.class, priority = 1500)
public class ModelPartMixin {
    /// Render is single threaded, so a plain counter is enough. Kept balanced by a `finally`, so a
    /// throwing model cannot leave the tint permanently suppressed.
    @Unique
    private static int spellEngine_tintDepth = 0;

    @WrapMethod(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V")
    private void spellEngine_applyEntityTint(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                             float red, float green, float blue, float alpha, Operation<Void> original) {
        if (spellEngine_tintDepth == 0 && EntityTints.Current.isActive()) {
            var argb = EntityTints.Current.argb();
            red *= ((argb >> 16) & 0xFF) / 255F;
            green *= ((argb >> 8) & 0xFF) / 255F;
            blue *= (argb & 0xFF) / 255F;
            alpha *= (argb >>> 24) / 255F;
        }
        spellEngine_tintDepth += 1;
        try {
            original.call(matrices, vertices, light, overlay, red, green, blue, alpha);
        } finally {
            spellEngine_tintDepth -= 1;
        }
    }
}
