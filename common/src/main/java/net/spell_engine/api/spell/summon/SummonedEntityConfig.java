package net.spell_engine.api.spell.summon;

import net.minecraft.resources.Identifier;
import net.tiny_config.versioning.VersionableConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/// A data-driven base-attribute config-file model for summoned entities, reusable across content mods.
///
/// This is a *file model*, not a mandate: each content mod that wants file-backed config owns its own
/// instance (e.g. `config/wizards/summoned_entities.json`) and controls its own {@code schema_version}
/// independently of every other mod (it extends {@link VersionableConfig} so `ConfigManager.schemaVersion(int)`
/// applies). {@link #entries} is keyed by the **full entity id** (`namespace:path`, e.g.
/// `wizards:frost_elemental`).
///
/// SpellEngine's registration seam ({@link SummonedEntities#registerAttributes}) never touches this class:
/// it speaks only `Function<Identifier, Entry>`. This model is merely one convenient *source* of that
/// function — see {@link #entryFor} — leaving mods equally free to back the source with inline constants
/// and skip config files (and TinyConfig) entirely.
public class SummonedEntityConfig extends VersionableConfig {
    public LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    /// Resolves the entry for a full entity id, or {@code null} if this config has none. As a method
    /// reference ({@code config::entryFor}) this is exactly the `Function<Identifier, Entry>` that
    /// {@link SummonedEntities#registerAttributes} consumes.
    public Entry entryFor(Identifier entityId) {
        return entries.get(entityId.toString());
    }

    public static class Entry {
        public CommonAttributes common = new CommonAttributes();
        public List<CustomAttribute> custom = new ArrayList<>();
    }

    public static class CommonAttributes {
        public double max_health = 20;
        public double movement_speed = 0.25;
        public double attack_damage = 2;
        public double follow_range = 32;

        public CommonAttributes() {}

        public CommonAttributes(double max_health, double movement_speed, double attack_damage) {
            this.max_health = max_health;
            this.movement_speed = movement_speed;
            this.attack_damage = attack_damage;
        }
    }

    /// An additional attribute (typically a spell-power school, e.g. `spell_power:frost`) to add to the
    /// entity's default container, with its base value.
    public static class CustomAttribute {
        public String id = "";
        public double value = 0;

        public CustomAttribute() {}

        public CustomAttribute(String id, double value) {
            this.id = id;
            this.value = value;
        }
    }
}
