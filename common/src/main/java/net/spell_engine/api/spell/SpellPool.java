package net.spell_engine.api.spell;

import net.minecraft.util.Identifier;
import net.spell_power.api.MagicSchool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record SpellPool(List<Identifier> spellIds) {

    // MARK: Data file format

    public static class DataFormat { public DataFormat() { }
        public List<String> spell_ids = List.of();
        public List<MagicSchool> all_of_school = List.of();
    }

    public static SpellPool fromData(DataFormat json, Map<Identifier, Spell> spells) {
        var spellsIds = new LinkedHashSet<Identifier>(); // This collection type to remove duplicates
        if (json.spell_ids != null) {
            for (var idString: json.spell_ids) {
                var id = new Identifier(idString);
                if(spells.get(id) != null) {
                    spellsIds.add(id);
                }
            }
        }
        if (json.all_of_school != null && !json.all_of_school.isEmpty()) {
            for (var entry: spells.entrySet()) {
                if (json.all_of_school.contains(entry.getValue().school)) {
                    spellsIds.add(entry.getKey());
                }
            }
        }
        return new SpellPool(spellsIds.stream().toList());
    }

    // MARK: Sync format

    public SyncFormat toSync() {
        var formatted = new SyncFormat();
        formatted.spell_ids = spellIds.stream().map(Identifier::toString).toList();
        return formatted;
    }

    public static SpellPool fromSync(SyncFormat json) {
        return new SpellPool(json.spell_ids.stream().map(Identifier::new).toList());
    }

    public static class SyncFormat { public SyncFormat() { }
        public List<String> spell_ids = List.of();
    }
}
