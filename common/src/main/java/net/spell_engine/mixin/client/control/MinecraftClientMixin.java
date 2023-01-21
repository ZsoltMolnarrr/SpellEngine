package net.spell_engine.mixin.client.control;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientPlayerEntity player;

//    @Redirect(method = "handleInputEvents", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", opcode = Opcodes.PUTFIELD))
//    private void handleInputEvents_OverrideNumberKeys(PlayerInventory instance, int index) {
//        if (InputHelper.shouldControlSpellHotbar()) {
//            var caster = ((SpellCasterClient)player);
//            var container = caster.getCurrentContainer();
//            if (container != null && container.isUsable()) {
//                if (index >= container.spell_ids.size()) {
//                    return;
//                }
//            }
//            caster.setSelectedSpellIndex(index);
//        } else {
//            instance.selectedSlot = index;
//        }
//    }

    @WrapWithCondition(method = "handleInputEvents", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", ordinal = 0, opcode = Opcodes.PUTFIELD))
    private boolean handleInputEvents_OverrideNumberKeys(PlayerInventory instance, int index) {
        if (InputHelper.shouldControlSpellHotbar()) {
            var caster = ((SpellCasterClient)player);
            var container = caster.getCurrentContainer();
            if (container != null && container.isUsable()) {
                if (index >= container.spell_ids.size()) {
                    return false;
                }
            }
            caster.setSelectedSpellIndex(index);
            return false;
        } else {
            return true;
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

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick_HEAD_UnlockHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        var container = ((SpellCasterClient)player).getCurrentContainer();
        if (container == null || !container.isUsable() || container.spell_ids.isEmpty()) {
            InputHelper.isLocked = false;
        }
    }

    @Inject(method = "hasOutline", at = @At(value = "HEAD"), cancellable = true)
    private void hasOutline_HEAD_SpellEngine(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if(TargetHelper.isTargetedByPlayer(entity, player) && SpellEngineClient.config.highlightTarget) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
