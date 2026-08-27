package net.spell_engine.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;

public class Keybindings {
    public static final List<KeyMapping> all() {
        return mutableAll;
    }
    public static final ArrayList<KeyMapping> mutableAll = new ArrayList<>();

    private static KeyMapping add(KeyMapping keyBinding) {
        mutableAll.add(keyBinding);
        return keyBinding;
    }

    /// 1.21.9+ keybinding categories are registered objects. `KeyMapping.Category#label()` is
    /// `Component.translatable(id.toLanguageKey("key.category"))`, so the id below needs the lang key
    /// `key.category.spell_engine.spell_engine` (see `assets/spell_engine/lang/en_us.json`).
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_engine"));

    private static KeyMapping hotbarKey(int number) {
        var key = new KeyMapping(
                "keybindings." + SpellEngineMod.ID + ".spell_hotbar_" + number,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY);
        add(key);
        return key;
    }

    /**
     * Dedicated key for revealing detailed spell tooltips.
     * Unbound by default, in which case tooltips fall back to `bypass_spell_hotbar`.
     * GUI scoped, so it can share a key with an in-game binding (such as sneak) without disabling it.
     */
    public static KeyMapping tooltip_details = add(new KeyMapping(
            "keybindings." + SpellEngineMod.ID + ".tooltip_details",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY));

    public static KeyMapping bypass_spell_hotbar = add(new KeyMapping(
            "keybindings." + SpellEngineMod.ID + ".bypass_spell_hotbar",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LALT,
            CATEGORY));

    public static KeyMapping spell_hotbar_1 = hotbarKey(1);
    public static KeyMapping spell_hotbar_2 = hotbarKey(2);
    public static KeyMapping spell_hotbar_3 = hotbarKey(3);
    public static KeyMapping spell_hotbar_4 = hotbarKey(4);
    public static KeyMapping spell_hotbar_5 = hotbarKey(5);
    public static KeyMapping spell_hotbar_6 = hotbarKey(6);
    public static KeyMapping spell_hotbar_7 = hotbarKey(7);
    public static KeyMapping spell_hotbar_8 = hotbarKey(8);
    public static KeyMapping spell_hotbar_9 = hotbarKey(9);

    public static class Wrapped {
        public static List<WrappedKeybinding> all() {
            return List.of(
                    new WrappedKeybinding(Keybindings.spell_hotbar_1, SpellEngineClient.config.spell_hotbar_1_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_2, SpellEngineClient.config.spell_hotbar_2_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_3, SpellEngineClient.config.spell_hotbar_3_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_4, SpellEngineClient.config.spell_hotbar_4_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_5, SpellEngineClient.config.spell_hotbar_5_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_6, SpellEngineClient.config.spell_hotbar_6_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_7, SpellEngineClient.config.spell_hotbar_7_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_8, SpellEngineClient.config.spell_hotbar_8_defer),
                    new WrappedKeybinding(Keybindings.spell_hotbar_9, SpellEngineClient.config.spell_hotbar_9_defer)
            );
        }
    }
}
