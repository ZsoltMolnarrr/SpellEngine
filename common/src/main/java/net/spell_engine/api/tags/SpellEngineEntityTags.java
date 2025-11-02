package net.spell_engine.api.tags;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpellEngineEntityTags {
    /**
     * Entities that are considered bosses.
     * Movement impairing and stun effects are disabled against these.
     */
    public static final TagKey<EntityType<?>> bosses = TagKey.of(Registries.ENTITY_TYPE.getKey(), Identifier.of(SpellEngineMod.ID, "bosses"));

    /**
     * Categories of entities that are considered mechanical.
     */
    public static final TagKey<EntityType<?>> mechanical = TagKey.of(Registries.ENTITY_TYPE.getKey(), Identifier.of(SpellEngineMod.ID, "mechanical"));

    public static class Vulnerability {
        enum Category {
            WEAK_TO,
            RESISTANT_TO
        }
        public record Entry(SpellSchool school, Category category, List<TagKey<EntityType<?>>> included) {
            public Identifier id() {
                return Identifier.of(SpellEngineMod.ID, "vulnerability/" + category.name().toLowerCase(Locale.ROOT) + "_" + school.id.getPath());
            }
            public TagKey<EntityType<?>> tag() {
                return TagKey.of(Registries.ENTITY_TYPE.getKey(), id());
            }
        }
        public static final List<Entry> ALL = new ArrayList<>();
        public static Entry add(Entry entry) {
            ALL.add(entry);
            return entry;
        }

        public static final Entry WEAK_TO_FIRE = add(new Entry(SpellSchools.FIRE, Category.WEAK_TO, List.of(
                EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES
        )));
        public static final Entry WEAK_TO_FROST = add(new Entry(SpellSchools.FROST, Category.WEAK_TO, List.of(
                EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES
        )));
        public static final Entry RESISTANT_TO_FROST = add(new Entry(SpellSchools.FROST, Category.RESISTANT_TO, List.of(
                EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES
        )));
        public static final Entry WEAK_TO_HOLY = add(new Entry(SpellSchools.HEALING, Category.WEAK_TO, List.of(
                EntityTypeTags.UNDEAD
        )));
    }
}
