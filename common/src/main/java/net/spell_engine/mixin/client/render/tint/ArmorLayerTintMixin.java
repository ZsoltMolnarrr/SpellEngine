package net.spell_engine.mixin.client.render.tint;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.render.CustomLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Worn armor follows the body into transparency while the entity carries a translucent tint.
///
/// The body is handled in `LivingEntityRendererMixin`, by swapping the layer its renderer returns. Armor
/// is drawn from layers looked up here instead, by whoever is drawing it - the vanilla feature renderer,
/// a custom armor model, an elytra - so the substitution is made at the lookup, and every one of them
/// picks it up without knowing.
///
/// Substituting here, rather than blending the vanilla layer once at startup, is the whole point: see
/// [CustomLayers#armorTranslucent]. [EntityTints.Current] is set only around a living entity's render, so
/// nothing outside one - the layer built during `TexturedRenderLayers` class init included - is affected.
@Mixin(RenderLayer.class)
public class ArmorLayerTintMixin {
    @ModifyReturnValue(method = "getArmorCutoutNoCull", at = @At("RETURN"), require = 1)
    private static RenderLayer getArmorCutoutNoCull_RETURN_SpellEngine_Tint(RenderLayer original, Identifier texture) {
        return EntityTints.Current.isTranslucent()
                ? CustomLayers.armorTranslucent(texture)
                : original;
    }
}
