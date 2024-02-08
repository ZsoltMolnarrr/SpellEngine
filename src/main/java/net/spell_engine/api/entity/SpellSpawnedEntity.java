package net.spell_engine.api.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public interface SpellSpawnedEntity {
    void onCreatedFromSpell(LivingEntity owner, Identifier spellId);
}
