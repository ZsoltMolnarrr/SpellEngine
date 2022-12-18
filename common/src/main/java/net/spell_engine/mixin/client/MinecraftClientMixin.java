package net.spell_engine.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.internals.SpellCasterClient;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Redirect(method = "handleInputEvents", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", opcode = Opcodes.PUTFIELD))
    private void handleInputEvents_OverrideNumberKeys(PlayerInventory instance, int index) {
        if (InputHelper.shouldControlSpellHotbar()) {
            var caster = ((SpellCasterClient)player);
            var container = caster.getCurrentContainer();
            if (container != null && container.isValid()) {
                if (index >= container.spell_ids.size()) {
                    return;
                }
            }
            caster.setSelectedSpellIndex(index);
        } else {
            instance.selectedSlot = index;
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_SpellHotbarLock(CallbackInfo ci) {
        if (Keybindings.hotbarLock.wasPressed()) {
            InputHelper.toggleLock();
        }
    }

    @Inject(method = "openPauseMenu", at = @At(value = "HEAD"), cancellable = true)
    private void openPauseMenu_HEAD_UnlockHotbar(boolean pause, CallbackInfo ci) {
        if (SpellEngineClient.config.unlockHotbarOnEscape && InputHelper.isLocked) {
            InputHelper.isLocked = false;
            ci.cancel();
        }
    }
}
