package net.spell_engine.api.tags;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

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
}
