package net.spell_engine.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.spell_engine.SpellEngineMod;

import java.util.List;

public class Keybindings {
    public static KeyBinding hotbarModifier;
    public static final List<KeyBinding> all;

    static {
        hotbarModifier = new KeyBinding(
                "keybinds." + SpellEngineMod.ID + ".hotbar_modifier",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_LEFT_ALT,
                SpellEngineMod.modName());

        all = List.of(hotbarModifier);
    }
}
