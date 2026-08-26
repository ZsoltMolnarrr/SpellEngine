package net.spell_engine.api.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;

public class SpellEntity {
    public interface Spawned {
        record Args(LivingEntity owner, Holder<Spell> spell, Spell.Impact.Action.Spawn spawnData, SpellExecution.ImpactContext context) { }
        void onSpawnedBySpell(Args args);
    }
}
