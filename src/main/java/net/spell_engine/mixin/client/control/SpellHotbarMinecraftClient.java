package net.spell_engine.mixin.client.control;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.client.input.WrappedKeybinding;
import net.spell_engine.internals.casting.SpellCasterClient;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(MinecraftClient.class)
public abstract class SpellHotbarMinecraftClient {
    @Shadow @Nullable public ClientPlayerEntity player;
    @Shadow @Final public GameOptions options;
    @Shadow private int itemUseCooldown;
    @Shadow public int attackCooldown;

    @Shadow @Nullable public Screen currentScreen;
    @Nullable private WrappedKeybinding.Category spellHotbarHandle = null;
    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        spellHotbarHandle = null;
        if (player == null || options == null) { return; }

        // Update the content of the Spell Hotbar
        // This needs to run every tick because the player's held caster item may change any time
        var hotbarUpdated = SpellHotbar.INSTANCE.update(player, options);
        if (hotbarUpdated) {
            itemUseCooldown = 5;
        }
        SpellHotbar.INSTANCE.prepare(itemUseCooldown);

        SpellHotbar.Handle handled;
        if (SpellEngineClient.config.useKeyHighPriority
                || ((SpellCasterClient)player).isCastingSpell()
                || (player.isUsingItem() && SpellHotbar.INSTANCE.lastHandledWasItemBypass()) ) {
            handled = SpellHotbar.INSTANCE.handle(player, options);
        } else {
            handled = SpellHotbar.INSTANCE.handle(player, SpellHotbar.INSTANCE.structuredSlots.other(), options);
        }
        if (handled != null) {
            spellHotbarHandle = handled.category();
//            if (handled.spell().spell().mode == Spell.Mode.ITEM_USE
//                    && !handled.keyBinding().equals(options.useKey)  ) {
//                doItemUse();
//            }
        }

        if (((SpellCasterClient)player).isCastingSpell()) {
            attackCooldown = 2;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        if (currentScreen != null) {
            ((SpellCasterClient)player).cancelSpellCast();
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "TAIL"))
    private void handleInputEvents_TAIL_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        popConflictingPressState();
        SpellHotbar.INSTANCE.syncItemUseSkill(player);
    }

    private Map<KeyBinding, Boolean> conflictingPressState = new HashMap<>();
    private void pushConflictingPressState(WrappedKeybinding.Category spellHotbarHandle, boolean value) {
        if (spellHotbarHandle != null) {
            switch (spellHotbarHandle) {
                case USE_KEY -> {
                    conflictingPressState.put(options.useKey, options.useKey.isPressed());
                    options.useKey.setPressed(value);
                }
                case ITEM_HOTBAR_KEY -> {
                    // This case is better handled by `handleInputEvents_OverrideNumberKeys`
                    break;
//                    for (var hotbarKey : options.hotbarKeys) {
//                        conflictingPressState.put(hotbarKey, hotbarKey.isPressed());
//                        hotbarKey.setPressed(value);
//                        if (!value) {
//                            ((KeybindingAccessor) hotbarKey).spellEngine_reset();
//                        }
//                    }
                }
            }
        }
    }

    private void popConflictingPressState() {
        for (var entry : conflictingPressState.entrySet()) {
            entry.getKey().setPressed(entry.getValue());
        }
        conflictingPressState.clear();
    }

    @WrapWithCondition(method = "handleInputEvents", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", ordinal = 0, opcode = Opcodes.PUTFIELD))
    private boolean handleInputEvents_OverrideNumberKeys(PlayerInventory instance, int index) {
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
            return false;
        } else {
            return true;
        }
    }
}