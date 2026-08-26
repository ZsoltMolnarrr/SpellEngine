package net.spell_engine.mixin.client.render.item;

import net.spell_engine.client.render.extension.ItemRenderStateExtension;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Resolves the glow the holder casts onto the item at the only point that sees both the item and who
/// holds it. `clearAndUpdate` is the common entry of every held-item path: third person goes through
/// `updateForLivingEntity`, first person (`HeldItemRenderer`) calls it directly with the player as the
/// `HeldItemContext`. Items without a holder (GUI, ground, item frames) pass no context and never glow.
@Mixin(ItemModelResolver.class)
public class ItemModelManagerMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void clearAndUpdate_TAIL_SpellEngine_itemGlow(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                                         @Nullable Level world, @Nullable ItemOwner heldItemContext, int seed, CallbackInfo ci) {
        // Hand contexts only: GUI/ground/fixed/head contexts never glow, even when a holder is passed along
        var inHand = displayContext.firstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var glow = inHand && heldItemContext instanceof LivingEntity entity ? GlowingItemStatusEffect.resolve(entity, stack) : null;
        ((ItemRenderStateExtension) renderState).spellEngine_setGlow(glow);
    }
}
