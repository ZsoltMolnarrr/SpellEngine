package net.spell_engine.rpg_series.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.rpg_series.RPGSeriesCore;

import java.util.LinkedHashMap;
import java.util.Locale;

public class RPGSeriesItemTags {
    public static final String NAMESPACE = RPGSeriesCore.NAMESPACE;

    public static class WeaponType {
        public static final String FOLDER = "weapon_type";
        public static final LinkedHashMap<Equipment.WeaponType, TagKey<Item>> ALL = new LinkedHashMap<>();
        static {
            for(var category: Equipment.WeaponType.values()) {
                var id = Identifier.of(NAMESPACE, FOLDER + "/" + category.toString().toLowerCase(Locale.ROOT));
                var tag = TagKey.of(RegistryKeys.ITEM, id);
                ALL.put(category, tag);
            }
        }

        public static TagKey<Item> get(Equipment.WeaponType category) {
            return ALL.get(category);
        }
        public static String tagString(Equipment.WeaponType category) {
            return "#" + get(category).id().toString();
        }
    }

    public enum ArmorMetaType {
        MELEE, MAGIC, ARCHERY
    }
    public static class ArmorType {
        public static final String FOLDER = "armor_type";
        public static final LinkedHashMap<ArmorMetaType, TagKey<Item>> ALL = new LinkedHashMap<>();
        public static Identifier id(ArmorMetaType category) {
            return Identifier.of(NAMESPACE, FOLDER + "/" + category.toString().toLowerCase(Locale.ROOT));
        }
        static {
            for(var category: ArmorMetaType.values()) {
                var tag = TagKey.of(RegistryKeys.ITEM, id(category));
                ALL.put(category, tag);
            }
        }
        public static TagKey<Item> get(ArmorMetaType category) {
            return ALL.get(category);
        }
    }

    public enum RoleArchetype {
        MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, DEFENSE, HEALING
    }
    public static class Archetype {
        public static final String FOLDER = "archetype";
        public static final LinkedHashMap<RoleArchetype, TagKey<Item>> TAGS = new LinkedHashMap<>();
        public static Identifier id(RoleArchetype archetype) {
            return Identifier.of(NAMESPACE, FOLDER + "/" + archetype.toString().toLowerCase(Locale.ROOT) + "_weapon");
        }
        static {
            for(var archetype: RoleArchetype.values()) {
                var tag = TagKey.of(RegistryKeys.ITEM, id(archetype));
                TAGS.put(archetype, tag);
            }
        }

        public static String tagString(RoleArchetype archetype) {
            return "#" + id(archetype);
        }

        public static TagKey<Item> tag(RoleArchetype archetype) {
            return TAGS.get(archetype);
        }

        public static RoleArchetype classify(Equipment.WeaponType category) {
            switch (category) {
                case DAMAGE_STAFF, DAMAGE_WAND, SPELL_SCYTHE, SPELL_BLADE -> {
                    return RoleArchetype.MAGIC_DAMAGE;
                }
                case HEALING_STAFF, HEALING_WAND -> {
                    return RoleArchetype.HEALING;
                }
                case SHORT_BOW, LONG_BOW, RAPID_CROSSBOW, HEAVY_CROSSBOW -> {
                    return RoleArchetype.RANGED_DAMAGE;
                }
                case CLAYMORE, MACE, HAMMER, SPEAR,
                     DAGGER, SICKLE, DOUBLE_AXE, GLAIVE -> {
                    return RoleArchetype.MELEE_DAMAGE;
                }
                case SHIELD -> {
                    return RoleArchetype.DEFENSE;
                }
            }
            assert true;
            return null;
        }
    }

    public enum LootTheme {
        DRAGON, AETHER, GOLDEN_WEAPON
    }
    public static class LootThemes {
        public static final String FOLDER = "loot_theme";
        public static final LinkedHashMap<LootTheme, TagKey<Item>> ALL = new LinkedHashMap<>();

        public static TagKey<Item> get(String theme) {
            return TagKey.of(RegistryKeys.ITEM, id(theme.toLowerCase(Locale.ROOT)));
        }

        public static Identifier id(LootTheme theme) {
            return id(theme.toString().toLowerCase(Locale.ROOT));
        }

        public static Identifier id(String theme) {
            return Identifier.of(NAMESPACE, FOLDER + "/" + theme);
        }

        static {
            for(var theme: LootTheme.values()) {
                var id = id(theme.toString().toLowerCase(Locale.ROOT));
                var tag = TagKey.of(RegistryKeys.ITEM, id);
                ALL.put(theme, tag);
            }
        }
    }

    public enum LootCategory {
        WEAPONS, ARMORS, ACCESSORIES, RELICS
    }
    public static class LootTiers {
        public static final int DEFAULT_TIERS = 10;
        public static final String FOLDER = "loot_tier";
        public static TagKey<Item> get(int tier, LootCategory category) {
            return TagKey.of(RegistryKeys.ITEM, id(tier, category));
        }
        public static Identifier id(int tier, LootCategory category) {
            var name = category.toString().toLowerCase(Locale.ROOT);
            return Identifier.of(NAMESPACE, FOLDER + "/" + "tier_" + tier + "_" + name);
        }
    }
}
