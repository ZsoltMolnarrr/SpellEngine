package net.spell_engine.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getOffHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack renderHotbar_SpellEngine(
            // Mixin parameters
            PlayerEntity player, Operation<ItemStack> original
    ) {
        if (SpellEngineClient.config.spellHotbarShowsOffhand && SpellHotbar.INSTANCE.showsOffHandUse) {
            return ItemStack.EMPTY;
        } else {
            return original.call(player);
        }
    }
}
