package net.spell_engine.internals.casting;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
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
    public record Process(Identifier id, Spell spell, ItemStack itemStack, float speed, int length, long startedAt) {
        public int spellCastTicksSoFar(long worldTime) {
            // At least zero
            // The difference must fit into an integer
            return (int)Math.max(worldTime - startedAt, 0);
        }

        public Progress progress(int castTicks) {
            if (length <= 0) {
                return new Progress(1F, this);
            }
            float ratio = Math.min(((float)castTicks) / length(), 1F);
            return new Progress(ratio, this);
        }

        public Progress progress(long worldTime) {
            int castTicks = spellCastTicksSoFar(worldTime);
            return progress(castTicks);
        }
    }
    public record Progress(float ratio, Process process) { }

    public enum Mode {
        INSTANT, CHARGE, CHANNEL, ITEM_USE;
        public static Mode from(Spell spell) {
            switch (spell.mode) {
                case CAST -> {
                    if (spell.cast.duration <= 0) {
                        return INSTANT;
                    }
                    return SpellHelper.isChanneled(spell) ? CHANNEL : CHARGE;
                }
                case BYPASS_TO_ITEM_USE -> {
                    return ITEM_USE;
                }
            }
            return null; // Should never happen
        }
    }

    public enum Action {
        CHANNEL,
        RELEASE
    }

    public enum Animation {
        CASTING, RELEASE
    }
}
