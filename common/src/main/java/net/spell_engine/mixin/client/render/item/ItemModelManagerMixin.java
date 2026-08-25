package net.spell_engine.mixin.client.render.item;

import net.spell_engine.client.render.extension.ItemRenderStateExtension;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Resolves the glow the holder casts onto the item at the only point that sees both the item and who
/// holds it. Items without a holder (GUI, ground, item frames) go through other update paths and never glow.
@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
    @Inject(method = "updateForLivingEntity", at = @At("TAIL"))
    private void updateForLivingEntity_TAIL_SpellEngine_itemGlow(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, LivingEntity entity, CallbackInfo ci) {
        ((ItemRenderStateExtension) renderState).spellEngine_setGlow(GlowingItemStatusEffect.resolve(entity, stack));
    }
}
