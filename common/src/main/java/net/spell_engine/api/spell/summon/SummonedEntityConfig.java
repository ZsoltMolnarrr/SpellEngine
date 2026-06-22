package net.spell_engine.api.spell.summon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/// Central, data-driven base-attribute config for summoned entities, shared across content mods.
///
/// Hosted by SpellEngine as a single config file (`config/spell_engine/summoned_entities.json`).
/// {@link #entries} is keyed by the **full entity id** (`namespace:path`, e.g. `wizards:frost_elemental`),
/// so entries from different mods never collide. Content mods seed their default entries via
/// {@link SummonedEntities#registerAttributes}.
public class SummonedEntityConfig {
    public LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

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
