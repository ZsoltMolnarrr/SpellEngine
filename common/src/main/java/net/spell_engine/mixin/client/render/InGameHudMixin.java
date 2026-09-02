package net.spell_engine.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the off-hand item from the vanilla hotbar while the spell hotbar shows the item-use slot
 * ({@code spellHotbarHidesOffhand}). This is deliberately kept as a mixin: the loaders' native HUD
 * registries (Fabric {@code HudElementRegistry.replaceElement(HOTBAR, …)}, NeoForge
 * {@code RegisterGuiLayersEvent.wrapLayer(HOTBAR, …)}) can only wrap the whole hotbar draw, which reads
 * {@code player.getOffHandStack()} internally; the mixin-free alternative would be to blank the player's
 * off-hand inventory slot around the wrapped vanilla call, i.e. mutate game state during rendering.
 * Everything else about the spell HUD is registered natively, see {@code HudRenderHelper.renderHudElement}.
 */
@Mixin(Hud.class) // 26.2: the in-game HUD (hotbar included) split out of `Gui` into `Hud`
public class InGameHudMixin {
    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack renderHotbar_SpellEngine(
            // Mixin parameters
            Player player, Operation<ItemStack> original
    ) {
        if (SpellEngineClient.config.spellHotbarHidesOffhand && SpellHotbar.INSTANCE.isShowingItemUse()) {
            return ItemStack.EMPTY;
        } else {
            return original.call(player);
        }
    }

}
