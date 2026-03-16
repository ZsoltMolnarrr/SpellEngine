package net.spell_engine.mixin.client.control;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.*;
import net.spell_engine.compat.CombatRollCompat;
import net.spell_engine.internals.casting.SpellCasterClient;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MinecraftClient.class, priority = 999)
public abstract class SpellHotbarMinecraftClient implements MinecraftClientExtension {
    @Shadow @Nullable public ClientPlayerEntity player;
    @Shadow @Final public GameOptions options;
    @Shadow private int itemUseCooldown;
    @Shadow public int attackCooldown;
    @Shadow @Nullable public Screen currentScreen;

    // @Nullable private WrappedKeybinding.Category spellHotbarHandle = null;

    private boolean useKeySpellCastingLock = false;

    // Holds the list of keys that are currently being used for something else.
    private final List<KeyBinding> concurrentKeys = new ArrayList<>();

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        // spellHotbarHandle = null;
        if (player == null || options == null) { return; }

        // Update the content of the Spell Hotbar
        // This needs to run every tick because the player's held caster item may change any time
        var hotbarUpdated = SpellHotbar.INSTANCE.update(player, options);
        if (hotbarUpdated) {
            itemUseCooldown = 4;
        }
        SpellHotbar.INSTANCE.prepare(itemUseCooldown);
        if (player.isUsingItem()) {
            return;
        }
        var caster = (SpellCasterClient) player;
        if (caster.getCurrentSkillAttack() != null) {
            itemUseCooldown = Math.max(itemUseCooldown, 1); // Blocking item use
            attackCooldown = 1; // Blocking attacks
            // Prevent spell cast start until the attack is finished
            // but allow ongoing process to continue
            if (caster.getSpellCastProcess() == null) {
                return;
            }
        }

        concurrentKeys.removeIf(k -> !k.isPressed());
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
                player.stopUsingItem();
                itemUseCooldown = 1;
            }
            if ( (handled.isSuccessfulAttempt() || ((SpellCasterClient)player).isCastingSpell())
                    && handled.keyBinding() == options.useKey) {
                useKeySpellCastingLock = true;
            }
        }
        if (useKeySpellCastingLock && !options.useKey.isPressed()) {
            useKeySpellCastingLock = false;
        }
        if (((SpellCasterClient)player).isCastingSpell()) {
            attackCooldown = 2;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        if (currentScreen != null || CombatRollCompat.isRolling.apply(player)) {
            ((SpellCasterClient)player).cancelSpellCast();
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "TAIL"))
    private void handleInputEvents_TAIL_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        SpellHotbar.INSTANCE.syncItemUseSkill(player);
    }

    @WrapOperation(
            method = "handleInputEvents",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", ordinal = 0, opcode = Opcodes.PUTFIELD),
            require = 0
    )
    private void selectSlot_Wrap(PlayerInventory instance, int index, Operation<Void> original) {
        var shouldControlSpellHotbar = false;
        if (!Keybindings.bypass_spell_hotbar.isPressed()) {
            for (var slot: SpellHotbar.INSTANCE.slots) {
                var keyBinding = slot.getKeyBinding(options);
                if (options.hotbarKeys[index] == keyBinding) {
                    shouldControlSpellHotbar = true;
                    break;
                }
            }
        }

        if (shouldControlSpellHotbar) {
            // Do nothing
        } else {
            var trigger = this.options.hotbarKeys[index];
            concurrentKeys.add(trigger);
            original.call(instance, index);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void doItemUse_HEAD_autoSwap(CallbackInfo ci) {
        if (useKeySpellCastingLock
                || ((SpellCasterClient)player).isCastingSpell()) {
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