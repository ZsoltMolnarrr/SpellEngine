package net.spell_engine.api.spell;

import java.util.ArrayList;
import java.util.List;

public class SpellContainer { public SpellContainer() { }
    public boolean is_proxy = false;
    public int max_spell_count = 0;
    public String pool;
    public List<String> spell_ids = List.of();

    public SpellContainer(boolean is_proxy, String pool, int max_spell_count, List<String> spell_ids) {
        this.is_proxy = is_proxy;
        this.pool = pool;
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
        if (is_proxy) {
            return true;
        }
        if (max_spell_count < 0) {
            return false;
        }
        return !spell_ids.isEmpty() || (pool != null && !pool.isEmpty());
    }

    public boolean isUsable() {
        return isValid() && !spell_ids.isEmpty();
    }

    public SpellContainer copy() {
        return new SpellContainer(is_proxy, pool, max_spell_count, new ArrayList<>(spell_ids));
    }
}
