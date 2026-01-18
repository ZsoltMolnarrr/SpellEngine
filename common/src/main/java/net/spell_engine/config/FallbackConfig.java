package net.spell_engine.config;

import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.tags.SpellEngineItemTags;

import java.util.List;

public class FallbackConfig {
    public FallbackConfig() { }

    public static class CompatGroup {
        public boolean enabled = true;
        public String blacklist = "";
        public static class Specifier {
            public String item = "";
            public SpellContainer container;
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
        config.melee_weapons.blacklist = "#" + SpellEngineItemTags.NON_COMBAT_TOOLS.id().toString();
        return config;
    }
}
