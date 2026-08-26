package net.spell_engine.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import org.jetbrains.annotations.Nullable;

public class WrappedKeybinding {
    public enum Category {
        USE_KEY,
        ITEM_HOTBAR_KEY;
    }

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

        @Nullable
        public KeyMapping keyBindingFrom(Options options) {
            return switch (this) {
                case USE_KEY -> options.keyUse;
                case HOTBAR_KEY_1 -> options.keyHotbarSlots[0];
                case HOTBAR_KEY_2 -> options.keyHotbarSlots[1];
                case HOTBAR_KEY_3 -> options.keyHotbarSlots[2];
                case HOTBAR_KEY_4 -> options.keyHotbarSlots[3];
                case HOTBAR_KEY_5 -> options.keyHotbarSlots[4];
                case HOTBAR_KEY_6 -> options.keyHotbarSlots[5];
                case HOTBAR_KEY_7 -> options.keyHotbarSlots[6];
                case HOTBAR_KEY_8 -> options.keyHotbarSlots[7];
                case HOTBAR_KEY_9 -> options.keyHotbarSlots[8];
                default -> null;
            };
        }
    }

    public KeyMapping original;
    public VanillaAlternative alternative;

    public WrappedKeybinding(KeyMapping original, VanillaAlternative alternative) {
        this.original = original;
        this.alternative = alternative;
    }

    public record Unwrapped(KeyMapping keyBinding, @Nullable Category vanillaHandle) { }
    @Nullable
    public Unwrapped get(Options options) {
        var assignedKey = ((KeybindingAccessor)original).spellEngine_getBoundKey();
        if (assignedKey != null && assignedKey.getValue() != InputConstants.UNKNOWN.getValue()) {
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
