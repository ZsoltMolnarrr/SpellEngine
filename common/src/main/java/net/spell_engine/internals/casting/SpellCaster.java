package net.spell_engine.internals.casting;

import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.casting.ClientCastController;
import net.spell_engine.internals.arrow.ArrowShootContext;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.melee.Melee;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The spell-caster role hierarchy: {@link Entity} (anything exposing a cast process — players
/// and summons alike; what presentation keys on), {@link Player} (the interactor-backed player
/// casting surface) and {@link Client} (the local player's controller surface).
public class SpellCaster {

    /// The read surface every cast-visualization consumer keys on: any entity exposing a
    /// (synced) cast process. Implemented by players (via {@link Player}, backed by the
    /// interactor's process — or the local prediction) and by `SummonedEntity` (backed by its
    /// own synced channel process). Beam rendering, beam particles and the cast sound loop
    /// discover casters through this type, so any entity that provides a process gets the
    /// presentation free.
    public interface Entity {
        @Nullable SpellCast.Process getSpellCastProcess();

        @Nullable default Spell getCastedSpell() {
            var process = getSpellCastProcess();
            return process != null ? process.spell().value() : null;
        }

        default float getCastingSpeed() {
            var process = getSpellCastProcess();
            return process != null ? process.speed() : 1F;
        }

        default boolean isCastingSpell() {
            return getSpellCastProcess() != null;
        }

        @Nullable default Spell.Target.Beam getBeam() {
            var spell = getCastedSpell();
            if (spell != null && spell.target != null && spell.target.type == Spell.Target.Type.BEAM) {
                return spell.target.beam;
            }
            return null;
        }

        default boolean isBeaming() {
            return getBeam() != null;
        }
    }

    /// Access surface of a spell-casting player. The casting state itself lives in the
    /// {@link SpellCastInteractor}; the derived reads (current spell, beam, casting speed) come
    /// from {@link Entity} defaults over `getSpellCastProcess`, which here reads the interactor
    /// — the local client player overrides it with its predicted process, and the defaults
    /// compose with that via virtual dispatch.
    public interface Player extends Entity {
        /// The server-side casting authority component of this caster.
        /// Only meaningful on the server — client code must not signal it.
        SpellCastInteractor getInteractor();

        SpellCooldownManager getCooldownManager();

        @Override
        @Nullable default SpellCast.Process getSpellCastProcess() {
            return getInteractor().process();
        }

        // MARK: Delivery-stage state (arrow + melee) — not casting; left for a future cleanup

        void setArrowShootContext(ArrowShootContext shotContext);
        ArrowShootContext getArrowShootContext();

        void setMeleeSkillAttack(Melee.ActiveAttack attack);
        float getExtraSlipperiness();
        void setActiveMeleeSkill(RegistryEntry<Spell> spell);
        RegistryEntry<Spell> getActiveMeleeSkill();
    }

    /// Read-mostly access surface of the local casting player. The client-side casting state
    /// lives in the {@link ClientCastController}; the defaults below delegate there. The only
    /// command exposed is {@link #cancelSpellCast} — a legitimate external interrupt
    /// (impairment effects, compat); starting and releasing casts is input-driven and goes
    /// through the controller.
    public interface Client extends Player {
        /// The client-side casting counterpart of this player's {@link SpellCastInteractor}.
        /// One per player entity — state resets naturally when vanilla replaces the entity.
        ClientCastController getCastController();

        default List<net.minecraft.entity.Entity> getCurrentTargets() {
            return getCastController().currentTargets();
        }

        @Nullable default SpellCast.Progress getSpellCastProgress() {
            return getCastController().progress();
        }

        /// Interrupts the in-flight cast (the server decides what ending means for its mechanic).
        default void cancelSpellCast() {
            getCastController().cancelSpellCast();
        }

        // MARK: Melee delivery, client side — not casting; left for a future cleanup

        void onAttacksAvailable(List<Melee.Attack> attacks);
        Melee.ActiveAttack getCurrentSkillAttack();
    }
}
