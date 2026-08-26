package net.spell_engine.config;

import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.rpg_series.item.Weapons;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_engine.utils.PatternMatching;

import java.util.List;

public class FallbackConfig {
    public FallbackConfig() { }
    public boolean enabled = true;

    public static class CompatGroup {
        public boolean enabled = true;
        public String blacklist = "";
        public boolean enable_specifiers = true;
        public static class Specifier { public Specifier () { }
            public String item = "";
            public SpellContainer container;

            public Specifier(String item, SpellContainer container) {
                this.item = item;
                this.container = container;
            }
        }
        public List<Specifier> specifiers = List.of();
        public SpellContainer defaults;

        public CompatGroup() { }
        public CompatGroup(SpellContainer defaults) {
            this.defaults = defaults;
        }
    }
    public CompatGroup melee_weapons = new CompatGroup(SpellContainers.forMeleeWeapon());
    public CompatGroup ranged_weapons = new CompatGroup(SpellContainers.forRangedWeapon());

    public boolean isValid() {
        var allGroups = List.of(melee_weapons, ranged_weapons);
        for (var group: allGroups) {
            if (group.specifiers == null) {
                return false;
            }
            for (var specifier: group.specifiers) {
                if (specifier.container == null) {
                    return false;
                }
            }
            if (group.defaults == null) {
                return false;
            }
        }
        return true;
    }

    public static FallbackConfig defaults() {
        var config = new FallbackConfig();
        // ? Disable by default to protect large modpacks
        // config.melee_weapons.enable_specifiers = false;
        config.melee_weapons.specifiers = List.of(
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.CLAYMORE), Weapons.CLAYMORE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("claymore|great_sword|greatsword"), Weapons.CLAYMORE_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.HAMMER), Weapons.HAMMER_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("great_hammer|greathammer|war_hammer|warhammer|maul"), Weapons.HAMMER_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.DOUBLE_AXE), Weapons.DOUBLE_AXE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("double_axe|doubleaxe|war_axe|waraxe|great_axe|greataxe"), Weapons.DOUBLE_AXE_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.GLAIVE), Weapons.GLAIVE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("glaive"), Weapons.GLAIVE_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.SPEAR), Weapons.SPEAR_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("spear"), Weapons.SPEAR_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.SICKLE), Weapons.SICKLE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("sickle"), Weapons.SICKLE_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.DAGGER), Weapons.DAGGER_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("dagger|knife"), Weapons.DAGGER_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.MACE), Weapons.MACE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("mace|hammer|flail"), Weapons.MACE_CONTAINER),
                new CompatGroup.Specifier("#minecraft:axes", Weapons.AXE_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("_axe"), Weapons.AXE_CONTAINER),
                new CompatGroup.Specifier(RPGSeriesItemTags.WeaponType.tagString(Equipment.WeaponType.SWORD), Weapons.SWORD_CONTAINER),
                new CompatGroup.Specifier(PatternMatching.regex("sword|blade"), Weapons.SWORD_CONTAINER)
        );
        config.melee_weapons.blacklist = "#" + SpellEngineItemTags.NON_COMBAT_TOOLS.location().toString();
        return config;
    }
}
