package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.spell_engine.client.casting.ClientCastController;
import net.spell_engine.internals.melee.Melee;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Read-mostly access surface of the local casting player. The client-side casting state lives
/// in the {@link ClientCastController}; the defaults below delegate there. The only command
/// exposed is {@link #cancelSpellCast} — a legitimate external interrupt (impairment effects,
/// compat); starting and releasing casts is input-driven and goes through the controller.
public interface SpellCasterClient extends SpellCasterEntity {
    /// The client-side casting counterpart of this player's {@link SpellCastInteractor}.
    /// One per player entity — state resets naturally when vanilla replaces the entity.
    ClientCastController getCastController();

    default List<Entity> getCurrentTargets() {
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
