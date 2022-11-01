package net.combatspells.client.projectile;

import net.combatspells.api.spell.Spell;
import net.minecraft.entity.FlyingItemEntity;

public interface FlyingSpellEntity extends FlyingItemEntity {
    Spell.ProjectileData.Client.RenderMode renderMode();
}
