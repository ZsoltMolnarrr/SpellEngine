package net.spell_engine.rpg_series.item;

import net.minecraft.world.item.ToolMaterial;
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
        WOODEN(ToolMaterial.WOOD, "Wooden"),
        TIER_0(ToolMaterial.STONE, "T0"),
        TIER_1(ToolMaterial.IRON, "T1"),
        TIER_2(ToolMaterial.DIAMOND, "T2"),
        TIER_3(ToolMaterial.NETHERITE, "T3"),
        TIER_4(ToolMaterial.NETHERITE, "T4"),  // Modded materials (ruby, aeternium, etc.)
        TIER_5(ToolMaterial.NETHERITE, "T5"),  // Higher-tier modded materials
        GOLDEN(ToolMaterial.GOLD, "Golden");

        private final ToolMaterial vanillaMaterial;
        private final String displayName;

        Tier(ToolMaterial material, String displayName) {
            this.vanillaMaterial = material;
            this.displayName = displayName;
        }

        public ToolMaterial getVanillaMaterial() {
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
