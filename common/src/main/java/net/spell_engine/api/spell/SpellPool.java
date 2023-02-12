package net.spell_engine.api.spell;

import net.minecraft.util.Identifier;
import net.spell_power.api.MagicSchool;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record SpellPool(List<Identifier> spellIds, List<MagicSchool> schools) {

    public static final SpellPool empty = new SpellPool(List.of(), List.of());

    public boolean isEmpty() {
        return spellIds.isEmpty();
    }

    // MARK: Helpers

    @Nullable
    public MagicSchool firstSchool() {
        return schools().stream().findFirst().orElse(null);
    }

    // MARK: Data file format

    public static class DataFormat { public DataFormat() { }
        public List<String> spell_ids = List.of();
        public List<MagicSchool> all_of_schools = List.of();
    }

    public static SpellPool fromData(DataFormat json, Map<Identifier, Spell> spells) {
        // LinkedHashSet to avoid duplicates
        var spellsIds = new LinkedHashSet<Identifier>();
        var schools = new LinkedHashSet<MagicSchool>();
        if (json.spell_ids != null) {
            for (var idString: json.spell_ids) {
                var id = new Identifier(idString);
                var spell = spells.get(id);
                if(spell != null) {
                    spellsIds.add(id);
                    schools.add(spell.school);
                }
            }
        }
        if (json.all_of_schools != null && !json.all_of_schools.isEmpty()) {
            for (var entry: spells.entrySet()) {
                var id = entry.getKey();
                var spell = entry.getValue();
                if (json.all_of_schools.contains(spell.school)) {
                    spellsIds.add(id);
                    schools.add(spell.school);
                }
            }
        }
        return new SpellPool(spellsIds.stream().toList(), schools.stream().toList());
    }

    // MARK: Sync format

    public SyncFormat toSync() {
        var formatted = new SyncFormat();
        formatted.spell_ids = spellIds.stream().map(Identifier::toString).toList();
        formatted.schools = schools.stream().map(MagicSchool::toString).toList();
        return formatted;
    }

    public static SpellPool fromSync(SyncFormat json) {
        return new SpellPool(
                json.spell_ids.stream().map(Identifier::new).toList(),
                json.schools.stream().map(MagicSchool::valueOf).toList());
    }

    public static class SyncFormat { public SyncFormat() { }
        public List<String> spell_ids = List.of();
        public List<String> schools = List.of();
    }
}