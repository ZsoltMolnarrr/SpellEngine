package net.spell_engine.api.spell;

import net.spell_power.api.MagicSchool;

import java.util.ArrayList;
import java.util.List;

public class SpellContainer { public SpellContainer() { }
    public MagicSchool school;
    public int max_spell_count;
    public List<String> spell_ids;

    public SpellContainer(MagicSchool school, int max_spell_count, List<String> spell_ids) {
        this.school = school;
        this.max_spell_count = max_spell_count;
        this.spell_ids = spell_ids;
    }

    // MARK: Helpers

    public int cappedIndex(int selected) {
        if (spell_ids.isEmpty()) { return 0; }
        var remainder = selected % spell_ids.size();
        return (remainder >= 0) ? remainder : (remainder + spell_ids.size());
    }

    public String spellId(int selected) {
        if (spell_ids == null || spell_ids.isEmpty()) {
            return null;
        }
        var index = cappedIndex(selected);
        return spell_ids.get(index);
    }

    public boolean isValid() {
        return school != null && spell_ids != null && max_spell_count > 0;
    }

    public boolean isUsable() {
        return isValid() && !spell_ids.isEmpty();
    }

    public SpellContainer copy() {
        return new SpellContainer(school, max_spell_count, new ArrayList<>(spell_ids));
    }
}
