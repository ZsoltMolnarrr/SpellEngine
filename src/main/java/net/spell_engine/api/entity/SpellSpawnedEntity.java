package net.spell_engine.api.entity;

import net.minecraft.entity.LivingEntity;

public interface SpellSpawnedEntity {
    void onCreatedFromSpell(LivingEntity owner, String spellId);
}
