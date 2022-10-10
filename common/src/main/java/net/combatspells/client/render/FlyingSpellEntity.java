package net.combatspells.client.render;

import net.combatspells.api.spell.Spell;
import net.minecraft.entity.FlyingItemEntity;

public interface FlyingSpellEntity extends FlyingItemEntity {
    Spell.ProjectileData.Client.RenderMode renderMode();
}
