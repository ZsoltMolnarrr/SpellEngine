package net.spell_engine.client.render;

import net.minecraft.world.entity.projectile.ItemSupplier;

/// Marker for spell-owned flying entities, so the renderer can tell them apart from vanilla
/// `FlyingItemEntity`s. The models themselves come from `SpellProjectile#renderModels()`.
public interface FlyingSpellEntity extends ItemSupplier {
}
