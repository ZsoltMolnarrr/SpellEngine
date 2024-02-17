package net.spell_engine.api.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;

public interface SpellSpawnedEntity {
    void onCreatedFromSpell(LivingEntity owner, Identifier spellId, Spell.Impact.Action.Spawn spawnData);
}
