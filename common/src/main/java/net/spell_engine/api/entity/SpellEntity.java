package net.spell_engine.api.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;

public class SpellEntity {
    public interface Spawned {
        record Args(LivingEntity owner, RegistryEntry<Spell> spell, Spell.Impact.Action.Spawn spawnData, SpellHelper.ImpactContext context) { }
        void onSpawnedBySpell(Args args);
    }
}
