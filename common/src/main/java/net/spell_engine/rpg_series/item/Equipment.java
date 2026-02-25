package net.spell_engine.rpg_series.item;

import net.minecraft.item.ToolMaterials;
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

    public enum Tier {
        WOODEN(ToolMaterials.WOOD, "Wooden"),
        TIER_0(ToolMaterials.STONE, "T0"),
        TIER_1(ToolMaterials.IRON, "T1"),
        TIER_2(ToolMaterials.DIAMOND, "T2"),
        TIER_3(ToolMaterials.NETHERITE, "T3"),
        TIER_4(ToolMaterials.NETHERITE, "T4"),  // Modded materials (ruby, aeternium, etc.)
        TIER_5(ToolMaterials.NETHERITE, "T5"),  // Higher-tier modded materials
        GOLDEN(ToolMaterials.GOLD, "Golden");

        private final ToolMaterials vanillaMaterial;
        private final String displayName;

        Tier(ToolMaterials material, String displayName) {
            this.vanillaMaterial = material;
            this.displayName = displayName;
        }

        public ToolMaterials getVanillaMaterial() {
            return vanillaMaterial;
        }
        public String getDisplayName() {
            return displayName;
        }

        public int getNumber() {
            return switch (this) {
                case TIER_0, WOODEN, GOLDEN -> 0;
                case TIER_1 -> 1;
                case TIER_2 -> 2;
                case TIER_3 -> 3;
                case TIER_4 -> 4;
                case TIER_5 -> 5;
            };
        }
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
