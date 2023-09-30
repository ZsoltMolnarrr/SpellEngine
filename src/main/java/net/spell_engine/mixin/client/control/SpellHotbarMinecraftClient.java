package net.spell_engine.mixin.client.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.client.input.WrappedKeybinding;
import org.jetbrains.annotations.Nullable;
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

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        // Update the content of the Spell Hotbar
        // This needs to run every tick because the player's held caster item may change any time
        SpellHotbar.INSTANCE.update(player);
    }

    @Nullable private WrappedKeybinding.VanillaAlternative.Category spellHotbarHandle = null;
    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        spellHotbarHandle = null;
        if (player == null || options == null || itemUseCooldown > 0) { return; }
        spellHotbarHandle = SpellHotbar.INSTANCE.handle(player, options);
        pushConflictingPressState(spellHotbarHandle, false);
    }

    @Inject(method = "handleInputEvents", at = @At(value = "TAIL"))
    private void handleInputEvents_TAIL_SpellHotbar(CallbackInfo ci) {
        if (player == null || options == null) { return; }
        popConflictingPressState();
    }

    private Map<KeyBinding, Boolean> conflictingPressState = new HashMap<>();
    private void pushConflictingPressState(WrappedKeybinding.VanillaAlternative.Category spellHotbarHandle, boolean value) {
        if (spellHotbarHandle != null) {
            switch (spellHotbarHandle) {
                case USE_KEY -> {
                    conflictingPressState.put(options.useKey, options.useKey.isPressed());
                    options.useKey.setPressed(value);
                }
                case ITEM_HOTBAR_KEY -> {
                    for (var hotbarKey : options.hotbarKeys) {
                        conflictingPressState.put(hotbarKey, hotbarKey.isPressed());
                        hotbarKey.setPressed(value);
                        if (!value) {
                            ((KeybindingAccessor) hotbarKey).spellEngine_reset();
                        }
                    }
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
}