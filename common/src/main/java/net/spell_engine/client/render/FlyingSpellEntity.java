package net.spell_engine.client.render;

import net.spell_engine.api.spell.Spell;
import net.minecraft.entity.FlyingItemEntity;

public interface FlyingSpellEntity extends FlyingItemEntity {
    Spell.ProjectileData.Client renderData();
}
