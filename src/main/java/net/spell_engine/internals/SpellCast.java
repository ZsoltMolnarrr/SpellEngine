package net.spell_engine.internals;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

public class SpellCast {
    public record Attempt(Result result,
                          @Nullable MissingItemInfo missingItem,
                          @Nullable OnCooldownInfo onCooldown) {
        public enum Result { SUCCESS, MISSING_ITEM, ON_COOLDOWN, NONE }
        public record MissingItemInfo(Item item) { }
        public record OnCooldownInfo() { }

        public static Attempt none() {
            return new Attempt(Result.NONE, null, null);
        }

        public static Attempt success() {
            return new Attempt(Result.SUCCESS, null, null);
        }

        public static Attempt failMissingItem(MissingItemInfo missingItem) {
            return new Attempt(Result.MISSING_ITEM, missingItem, null);
        }

        public static Attempt failOnCooldown(OnCooldownInfo onCooldown) {
            return new Attempt(Result.ON_COOLDOWN, null, onCooldown);
        }

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }
        public boolean isFail() {
            return result != Result.SUCCESS && result != Result.NONE;
        }
    }

    public record Duration(float speed, int length) { }
    public record Process(Identifier id, Spell spell, ItemStack itemStack, float speed, int length) { }
    public record Progress(Identifier id, Spell spell, float ratio, int length) { }

    public enum Action {
        START, CHANNEL, RELEASE
    }

    public enum Animation {
        CASTING, RELEASE
    }
}
