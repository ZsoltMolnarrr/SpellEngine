package net.spell_engine.internals.casting;

import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

/// @deprecated Compatibility bridge for external mods casting players to this type (Better
/// Combat and friends: `getCurrentSpell()`, `isCastingSpell()` — both inherited from
/// {@link SpellCaster.Entity}). Migrate to {@link SpellCaster.Player}, or
/// {@link SpellCaster.Entity} for read-only casting state. Player entities still implement
/// this type (see `PlayerEntityMixin`) so existing casts keep working; SpellEngine itself
/// must not reference it.
@Deprecated
public interface SpellCasterEntity extends SpellCaster.Player {
    /// Used by BetterCombat
    @Nullable
    default Spell getCurrentSpell() {
        return getCastedSpell();
    }

//    default boolean isCastingSpell() {
//        return getSpellCastProcess() != null;
//    }
}
