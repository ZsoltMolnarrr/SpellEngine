package net.spell_engine.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;

import java.util.ArrayList;
import java.util.List;

public class Keybindings {
    public static final List<KeyBinding> all() {
        return mutableAll;
    }
    public static final ArrayList<KeyBinding> mutableAll = new ArrayList<>();
    public static final ArrayList<KeyBinding> spellHotbar = new ArrayList<>();

    private static KeyBinding add(KeyBinding keyBinding) {
        mutableAll.add(keyBinding);
        return keyBinding;
    }

    private static KeyBinding hotbar(KeyBinding keyBinding) {
        var key = add(keyBinding);
        spellHotbar.add(key);
        return keyBinding;
    }

    public static KeyBinding hotbarModifier = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".hotbar_modifier",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_LEFT_ALT,
            SpellEngineMod.modName()));

    public static KeyBinding spell_hotbar_1 = hotbar(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_1",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            SpellEngineMod.modName()));
    public static KeyBinding spell_hotbar_2 = hotbar(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_2",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            SpellEngineMod.modName()));
    public static KeyBinding spell_hotbar_3 = hotbar(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_3",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            SpellEngineMod.modName()));
    public static KeyBinding spell_hotbar_4 = hotbar(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".spell_hotbar_4",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            SpellEngineMod.modName()));

    public static class Wrapped {
        public static List<WrappedKeybinding> all() {
            return List.of(
                    new WrappedKeybinding(Keybindings.spell_hotbar_1, SpellEngineClient.config.spellHotbar_1_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_2, SpellEngineClient.config.spellHotbar_2_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_3, SpellEngineClient.config.spellHotbar_3_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_4, SpellEngineClient.config.spellHotbar_4_defer)
            );
        }
    }
}
