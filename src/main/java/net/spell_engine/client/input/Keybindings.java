package net.spell_engine.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.spell_engine.SpellEngineMod;

import java.util.ArrayList;
import java.util.List;

public class Keybindings {
    public static final List<KeyBinding> all() {
        return mutableAll;
    }
    private static final ArrayList<KeyBinding> mutableAll = new ArrayList<>();

    private static KeyBinding add(KeyBinding keyBinding) {
        mutableAll.add(keyBinding);
        return keyBinding;
    }

    public static KeyBinding hotbarModifier = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".hotbar_modifier",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_LEFT_ALT,
            SpellEngineMod.modName()));
    public static KeyBinding hotbarLock = add(new KeyBinding(
                "keybindings." + SpellEngineMod.ID + ".hotbar_lock",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_Z, // InputUtil.UNKNOWN_KEY.getCode(),
                SpellEngineMod.modName()));

    public static KeyBinding spell_hotbar_1 = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_1",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_1,
            SpellEngineMod.modName()));
    public static KeyBinding spell_hotbar_2 = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_2",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_2,
            SpellEngineMod.modName()));

    public static KeyBinding spell_hotbar_3 = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_3",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_3,
            SpellEngineMod.modName()));

    public static KeyBinding spell_hotbar_4 = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_4",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_4,
            SpellEngineMod.modName()));
}
