package net.spell_engine.client.input;

import net.minecraft.client.MinecraftClient;
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
            if (client.player != null) {
                var container = ((SpellCasterClient)client.player).getCurrentContainer();
                if (container != null && container.isValid()) {
                    isLocked = true;
                }
            }
        }
    }
}
