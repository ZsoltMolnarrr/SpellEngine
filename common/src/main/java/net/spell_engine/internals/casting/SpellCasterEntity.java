package net.spell_engine.internals.casting;

import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellCooldownManager;
import net.spell_engine.internals.arrow.ArrowShootContext;
import net.spell_engine.internals.melee.Melee;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    SpellCooldownManager getCooldownManager();

    void setChannelTickIndex(int channelTickIndex);
    int getChannelTickIndex();

    void setSpellCastProcess(@Nullable SpellCast.Process process);
    @Nullable SpellCast.Process getSpellCastProcess();

    Spell getCurrentSpell(); // Used by Better Combat compatibility
    float getCurrentCastingSpeed();

    // Used for Archery
    void setArrowShootContext(ArrowShootContext shotContext);
    ArrowShootContext getArrowShootContext();

    boolean isBeaming();
    @Nullable
    Spell.Target.Beam getBeam();

    void setMeleeSkillAttack(Melee.ActiveAttack attack);
    float getExtraSlipperiness();
    void setActiveMeleeSkill(RegistryEntry<Spell> spell);
    RegistryEntry<Spell> getActiveMeleeSkill();

    default boolean isCastingSpell() {
        return getSpellCastProcess() != null;
    }
}
