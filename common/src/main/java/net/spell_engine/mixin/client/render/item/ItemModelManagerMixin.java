package net.spell_engine.mixin.client.render.item;

import net.spell_engine.client.render.extension.ItemRenderStateExtension;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Resolves the glow the holder casts onto the item at the only point that sees both the item and who
/// holds it. `clearAndUpdate` is the common entry of every held-item path: third person goes through
/// `updateForLivingEntity`, first person (`HeldItemRenderer`) calls it directly with the player as the
/// `HeldItemContext`. Items without a holder (GUI, ground, item frames) pass no context and never glow.
@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
    @Inject(method = "clearAndUpdate", at = @At("TAIL"))
    private void clearAndUpdate_TAIL_SpellEngine_itemGlow(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                                         @Nullable World world, @Nullable HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        // Hand contexts only: GUI/ground/fixed/head contexts never glow, even when a holder is passed along
        var inHand = displayContext.isFirstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var glow = inHand && heldItemContext instanceof LivingEntity entity ? GlowingItemStatusEffect.resolve(entity, stack) : null;
        ((ItemRenderStateExtension) renderState).spellEngine_setGlow(glow);
    }
}
