package net.spell_engine.internals.casting;

import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.arrow.ArrowShootContext;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.melee.Melee;
import org.jetbrains.annotations.Nullable;

/// Access surface of a spell-casting entity. The casting state itself lives in the
/// {@link SpellCastInteractor}; everything below the two abstract accessors is a derived
/// read provided as a default, so implementors carry no casting logic of their own.
public interface SpellCasterEntity {
    /// The server-side casting authority component of this caster.
    /// Only meaningful on the server — client code must not signal it.
    SpellCastInteractor getInteractor();

    SpellCooldownManager getCooldownManager();

    // MARK: Derived casting reads. The local client player overrides `getSpellCastProcess`
    // with its predicted process; the other defaults compose with that via virtual dispatch.

    @Nullable default SpellCast.Process getSpellCastProcess() {
        return getInteractor().process();
    }

    /// Used by Better Combat compatibility
    @Nullable default Spell getCurrentSpell() {
        var process = getSpellCastProcess();
        return process != null ? process.spell().value() : null;
    }

    default float getCurrentCastingSpeed() {
        var process = getSpellCastProcess();
        return process != null ? process.speed() : 1F;
    }

    default boolean isCastingSpell() {
        return getSpellCastProcess() != null;
    }

    @Nullable default Spell.Target.Beam getBeam() {
        var spell = getCurrentSpell();
        if (spell != null && spell.target != null && spell.target.type == Spell.Target.Type.BEAM) {
            return spell.target.beam;
        }
        return null;
    }

    default boolean isBeaming() {
        return getBeam() != null;
    }

    // MARK: Delivery-stage state (arrow + melee) — not casting; left for a future cleanup

    void setArrowShootContext(ArrowShootContext shotContext);
    ArrowShootContext getArrowShootContext();

    void setMeleeSkillAttack(Melee.ActiveAttack attack);
    float getExtraSlipperiness();
    void setActiveMeleeSkill(RegistryEntry<Spell> spell);
    RegistryEntry<Spell> getActiveMeleeSkill();
}
