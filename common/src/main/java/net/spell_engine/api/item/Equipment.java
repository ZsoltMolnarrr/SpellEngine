package net.spell_engine.api.item;

import org.jetbrains.annotations.Nullable;

public class Equipment {
    public enum WeaponType {
        DAMAGE_STAFF,
        DAMAGE_WAND,
        HEALING_STAFF,
        HEALING_WAND,
        SHORT_BOW,
        LONG_BOW,
        RAPID_CROSSBOW,
        HEAVY_CROSSBOW,
        SWORD,
        CLAYMORE,
        MACE,
        HAMMER,
        SPEAR,
        DAGGER,
        SICKLE,
        DOUBLE_AXE,
        GLAIVE,
        SPELL_BLADE,
        SPELL_SCYTHE,
        SHIELD
    }
    public record LootProperties(int tier, @Nullable String theme) {
        public static final LootProperties EMPTY = new LootProperties(-1, null);
        public static LootProperties of(int tier) {
            return new LootProperties(tier, null);
        }
        public static LootProperties of(int tier, String theme) {
            return new LootProperties(tier, theme);
        }
        public static LootProperties of(String theme) {
            return new LootProperties(-1, theme);
        }
    }
}
