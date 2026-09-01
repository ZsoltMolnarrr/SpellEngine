package net.spell_engine.mixin.client.render.tint;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.render.CustomLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// The armor trim has to move with the armor it sits on. Leaving it on the vanilla sheet while the armor
/// blends is precisely the arrangement that hides trims under Iris - see [CustomLayers#armorTranslucent].
@Mixin(TexturedRenderLayers.class)
public class ArmorTrimsLayerTintMixin {
    @ModifyReturnValue(method = "getArmorTrims", at = @At("RETURN"), require = 1)
    private static RenderLayer getArmorTrims_RETURN_SpellEngine_Tint(RenderLayer original, boolean decal) {
        return EntityTints.Current.isTranslucent()
                ? CustomLayers.armorTrimsTranslucent(decal)
                : original;
    }
}
