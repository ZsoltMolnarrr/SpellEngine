package net.spell_engine.client.input;

public class InputHelper {
    public static boolean shouldControlSpellHotbar() {
        return Keybindings.hotbarModifier.isPressed();
    }
}
