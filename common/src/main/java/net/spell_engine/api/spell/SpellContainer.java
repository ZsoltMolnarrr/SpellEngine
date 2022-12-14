package net.spell_engine.api.spell;

import net.spell_damage.api.MagicSchool;

import java.util.List;

public class SpellContainer { public SpellContainer() { }
    public MagicSchool school;
    public int max_spell_count;
    public List<String> spell_ids;

    // MARK: Helpers

    public String spellId(int selected) {
        if (spell_ids == null || spell_ids.isEmpty()) {
            return null;
        }
        var remainder = selected % spell_ids.size();
        var index = (remainder >= 0) ? remainder : (remainder + spell_ids.size());
        return spell_ids.get(index);
    }
}
