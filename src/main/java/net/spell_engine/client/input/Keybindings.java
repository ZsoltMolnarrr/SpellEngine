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

    private static KeyBinding add(KeyBinding keyBinding) {
        mutableAll.add(keyBinding);
        return keyBinding;
    }

    private static KeyBinding hotbarKey(int number) {
        var key = new KeyBinding(
                "keybindings." + SpellEngineMod.ID + ".spell_hotbar_" + number,
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                SpellEngineMod.modName());
        add(key);
        return key;
    }

    public static KeyBinding ignore_spell_hotbar = add(new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".ignore_spell_hotbar",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_LEFT_ALT,
            SpellEngineMod.modName()));

    public static KeyBinding spell_hotbar_1 = hotbarKey(1);
    public static KeyBinding spell_hotbar_2 = hotbarKey(2);
    public static KeyBinding spell_hotbar_3 = hotbarKey(3);
    public static KeyBinding spell_hotbar_4 = hotbarKey(4);
    public static KeyBinding spell_hotbar_5 = hotbarKey(5);
    public static KeyBinding spell_hotbar_6 = hotbarKey(6);
    public static KeyBinding spell_hotbar_7 = hotbarKey(7);
    public static KeyBinding spell_hotbar_8 = hotbarKey(8);
    public static KeyBinding spell_hotbar_9 = hotbarKey(9);

    public static class Wrapped {
        public static List<WrappedKeybinding> all() {
            return List.of(
                    new WrappedKeybinding(Keybindings.spell_hotbar_1, SpellEngineClient.config.spellHotbar_1_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_2, SpellEngineClient.config.spellHotbar_2_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_3, SpellEngineClient.config.spellHotbar_3_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_4, SpellEngineClient.config.spellHotbar_4_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_5, SpellEngineClient.config.spellHotbar_5_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_6, SpellEngineClient.config.spellHotbar_6_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_7, SpellEngineClient.config.spellHotbar_7_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_8, SpellEngineClient.config.spellHotbar_8_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_9, SpellEngineClient.config.spellHotbar_9_defer)
            );
        }
    }
}
