package net.spell_engine.mixin.client.control;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.client.input.WrappedKeybinding;
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
public class SpellHotbarMinecraftClient {
    @Shadow @Nullable public ClientPlayerEntity player;
    @Shadow @Final public GameOptions options;
    @Shadow private int itemUseCooldown;

    @Nullable private WrappedKeybinding.Category spellHotbarHandle = null;
    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        spellHotbarHandle = null;
        if (player == null || options == null || itemUseCooldown > 0) { return; }

        // Update the content of the Spell Hotbar
        // This needs to run every tick because the player's held caster item may change any time
        SpellHotbar.INSTANCE.update(player, options);
        SpellHotbar.INSTANCE.prepare();

        if (SpellEngineClient.config.useKeyHighPriority) {
            spellHotbarHandle = SpellHotbar.INSTANCE.handle(player, options);
        } else {
            spellHotbarHandle = SpellHotbar.INSTANCE.handle(player, SpellHotbar.INSTANCE.structuredSlots.other(), options);
        }

        pushConflictingPressState(spellHotbarHandle, false);
    }

    @Inject(method = "handleInputEvents", at = @At(value = "TAIL"))
    private void handleInputEvents_TAIL_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        popConflictingPressState();
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
        for (var slot: SpellHotbar.INSTANCE.slots) {
            var keyBinding = slot.getKeyBinding(options);
            if (options.hotbarKeys[index] == keyBinding) {
                shouldControlSpellHotbar = true;
                break;
            }
        }

        if (shouldControlSpellHotbar) {
            return false;
        } else {
            return true;
        }
    }
}