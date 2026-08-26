package net.spell_engine.mixin.client.control;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.*;
import net.spell_engine.compat.CombatRollCompat;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Minecraft.class, priority = 999)
public abstract class SpellHotbarMinecraftClient implements MinecraftClientExtension {
    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Final public Options options;
    @Shadow private int rightClickDelay;
    @Shadow public int missTime;
    @Shadow @Nullable public Screen screen;

    // @Nullable private WrappedKeybinding.Category spellHotbarHandle = null;

    private boolean useKeySpellCastingLock = false;

    // Holds the list of keys that are currently being used for something else.
    private final List<KeyMapping> concurrentKeys = new ArrayList<>();

    @Inject(method = "handleKeybinds", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        // spellHotbarHandle = null;
        if (player == null || options == null) { return; }

        // Update the content of the Spell Hotbar
        // This needs to run every tick because the player's held caster item may change any time
        var hotbarUpdated = SpellHotbar.INSTANCE.update(player, options);
        if (hotbarUpdated) {
            rightClickDelay = 4;
        }
        SpellHotbar.INSTANCE.prepare(rightClickDelay);
        if (player.isUsingItem()) {
            return;
        }
        var caster = (SpellCaster.Client) player;
        if (caster.getCurrentSkillAttack() != null) {
            rightClickDelay = Math.max(rightClickDelay, 1); // Blocking item use
            missTime = 1; // Blocking attacks
            // Prevent spell cast start until the attack is finished
            // but allow ongoing process to continue
            if (caster.getSpellCastProcess() == null) {
                return;
            }
        }

        concurrentKeys.removeIf(k -> !k.isDown());
        SpellHotbar.Handle handled;
        if (useKeySpellCastingLock || SpellEngineClient.config.useKeyHighPriority) {
            handled = SpellHotbar.INSTANCE.handleAll(player, options, concurrentKeys);
        } else {
            handled = SpellHotbar.INSTANCE.handleOther(player, options, concurrentKeys);
        }
        onSpellHotbarInputHandled(handled);
    }

    public void onSpellHotbarInputHandled(SpellHotbar.Handle handled) {
        if (handled != null) {
            // spellHotbarHandle = handled.category();
            if (player.isUsingItem()) {
                player.releaseUsingItem();
                rightClickDelay = 1;
            }
            if ( (handled.isSuccessfulAttempt() || ((SpellCaster.Client)player).isCastingSpell())
                    && handled.keyBinding() == options.keyUse) {
                useKeySpellCastingLock = true;
            }
        }
        if (useKeySpellCastingLock && !options.keyUse.isDown()) {
            useKeySpellCastingLock = false;
        }
        if (((SpellCaster.Client)player).isCastingSpell()) {
            missTime = 2;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        if (screen != null || CombatRollCompat.isRolling.apply(player)) {
            ((SpellCaster.Client)player).cancelSpellCast();
        }
    }

    @Inject(method = "handleKeybinds", at = @At(value = "TAIL"))
    private void handleInputEvents_TAIL_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
    }

    @WrapOperation(
            method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V", ordinal = 0), // 1.21.11: hotbar keys call setSelectedSlot() instead of writing the field
            require = 0
    )
    private void selectSlot_Wrap(Inventory instance, int index, Operation<Void> original) {
        var shouldControlSpellHotbar = false;
        if (!Keybindings.bypass_spell_hotbar.isDown()) {
            for (var slot: SpellHotbar.INSTANCE.slots) {
                var keyBinding = slot.getKeyBinding(options);
                if (options.keyHotbarSlots[index] == keyBinding) {
                    shouldControlSpellHotbar = true;
                    break;
                }
            }
        }

        if (shouldControlSpellHotbar) {
            // Do nothing
        } else {
            var trigger = this.options.keyHotbarSlots[index];
            concurrentKeys.add(trigger);
            original.call(instance, index);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void doItemUse_HEAD_autoSwap(CallbackInfo ci) {
        if (useKeySpellCastingLock
                || ((SpellCaster.Client)player).isCastingSpell()) {
            ci.cancel();
            return;
        }

        // Auto swap right click is handled instead in ClientPlayerInteractionManagerMixin
        // to allow block interactions to be handled first
    }
    
    @Override public boolean isSpellCastLockActive() {
        return useKeySpellCastingLock;
    }
}