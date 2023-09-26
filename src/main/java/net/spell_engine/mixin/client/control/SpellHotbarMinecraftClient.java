package net.spell_engine.mixin.client.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.input.SpellHotbar;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Mixin(MinecraftClient.class)
public class SpellHotbarMinecraftClient {
    @Shadow @Nullable public ClientPlayerEntity player;
    @Shadow @Final public GameOptions options;

    private List<KeyBinding> expectedConflictingKeys = List.of();
    private void loadPotentiallyConflictingKeys() {
        if (expectedConflictingKeys.isEmpty()) {
            var list = new ArrayList<KeyBinding>();
            list.add(options.useKey);
            list.addAll(Arrays.stream(options.hotbarKeys).toList());
            expectedConflictingKeys = list;
        }
    }

    // Key: hotbar keybinding, Value: vanilla keybinding
    private HashMap<KeyBinding, KeyBinding> conflictingKeys = new HashMap<>();
    private void detectConflictingKeys() {
        conflictingKeys.clear();
        for(var hotbarBind: Keybindings.spellHotbar) {
            var hotbarKey = ((KeybindingAccessor) hotbarBind).getBoundKey();
            for (var vanillaBind: expectedConflictingKeys) {
                var vanillaKey = ((KeybindingAccessor) vanillaBind).getBoundKey();
                if (hotbarKey.equals(vanillaKey)) {
                    conflictingKeys.put(hotbarBind, vanillaBind);
                }
            }
        }
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        SpellHotbar.INSTANCE.update(player);

        // FIXME: Call these on event
        loadPotentiallyConflictingKeys();
        detectConflictingKeys();
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"), cancellable = true)
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        var handledKeybind = SpellHotbar.INSTANCE.handle(player);
        System.out.println("SpellHotbar handled: " + handledKeybind);
        if (handledKeybind != null) {
            for (var conflicting: conflictingKeys.values()) {
                ((KeybindingAccessor)conflicting).spellEngine_reset();
            }
        }
    }
}