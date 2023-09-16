package net.spell_engine.client.render;

import net.minecraft.entity.FlyingItemEntity;
import net.spell_engine.api.spell.Spell;

public interface FlyingSpellEntity extends FlyingItemEntity {
    Spell.ProjectileData.Client renderData();
}
