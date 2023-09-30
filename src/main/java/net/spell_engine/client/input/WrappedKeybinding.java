package net.spell_engine.client.input;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import org.jetbrains.annotations.Nullable;

public class WrappedKeybinding {
    public enum VanillaAlternative {
        NONE(null),
        USE_KEY(Category.USE_KEY),
        HOTBAR_KEY_1(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_2(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_3(Category.ITEM_HOTBAR_KEY),
        HOTBAR_KEY_4(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_5(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_6(Category.ITEM_HOTBAR_KEY),
        HOTBAR_KEY_7(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_8(Category.ITEM_HOTBAR_KEY), HOTBAR_KEY_9(Category.ITEM_HOTBAR_KEY);

        @Nullable
        public final Category category;

        VanillaAlternative(Category category) {
            this.category = category;
        }

        public enum Category {
            USE_KEY,
            ITEM_HOTBAR_KEY;
        }

        @Nullable
        public KeyBinding keyBindingFrom(GameOptions options) {
            return switch (this) {
                case USE_KEY -> options.useKey;
                case HOTBAR_KEY_1 -> options.hotbarKeys[0];
                case HOTBAR_KEY_2 -> options.hotbarKeys[1];
                case HOTBAR_KEY_3 -> options.hotbarKeys[2];
                case HOTBAR_KEY_4 -> options.hotbarKeys[3];
                case HOTBAR_KEY_5 -> options.hotbarKeys[4];
                case HOTBAR_KEY_6 -> options.hotbarKeys[5];
                case HOTBAR_KEY_7 -> options.hotbarKeys[6];
                case HOTBAR_KEY_8 -> options.hotbarKeys[7];
                case HOTBAR_KEY_9 -> options.hotbarKeys[8];
                default -> null;
            };
        }
    }

    public KeyBinding original;
    public VanillaAlternative alternative;

    public WrappedKeybinding(KeyBinding original, VanillaAlternative alternative) {
        this.original = original;
        this.alternative = alternative;
    }

    public record Unwrapped(KeyBinding keyBinding, @Nullable VanillaAlternative.Category vanillaHandle) { }
    @Nullable
    public Unwrapped get(GameOptions options) {
        var assignedKey = ((KeybindingAccessor)original).getBoundKey();
        if (assignedKey != null && assignedKey.getCode() != InputUtil.UNKNOWN_KEY.getCode()) {
            return new Unwrapped(original, null);
        }

        if (alternative != null) {
            var alternativeKey = alternative.keyBindingFrom(options);
            if (alternativeKey != null) {
                return new Unwrapped(alternativeKey, alternative.category);
            }
        }

        return null;
    }
}
