package net.spell_engine.client.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellCasterClient;

public class InputHelper {
    public static boolean isLocked = false;

    public static boolean shouldControlSpellHotbar() {
        return isLocked || Keybindings.hotbarModifier.isPressed();
    }

    public static boolean isLockAssigned() {
        return !Keybindings.hotbarLock.isUnbound();
    }

    public static void toggleLock() {
        toggleLock(false);
    }

    public static void toggleLock(boolean skipValidation) {
        var client = MinecraftClient.getInstance();
        if (isLocked) {
            isLocked = false;
        } else {
            if (skipValidation || hasUsableSpellContainer(client.player)) {
                isLocked = true;
            }
        }
    }

    private static boolean hasUsableSpellContainer(PlayerEntity player) {
        if (player != null) {
            var container = ((SpellCasterClient)player).getCurrentContainer();
            return container != null && container.isUsable();
        }
        return false;
    }

    public static void showLockedMessage(String key) {
        var client = MinecraftClient.getInstance();
        MutableText component = Text.translatable("hud.leave_spell_hotbar", key);
        client.inGameHud.setOverlayMessage(component, false);
    }

    public record HotbarVisibility(boolean item, boolean spell) { }

    public static HotbarVisibility hotbarVisibility() {
        var config = SpellEngineClient.config;
        boolean item = true;
        boolean spell = true;
        if (config.showFocusedHotbarOnly) {
            spell = isLocked;
            item = !spell;
        }
        return new HotbarVisibility(item, spell);
    }
}
