package net.spell_engine.api.effect;

import java.util.Collection;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/// Marks a status effect as granting immunity to knockback taken from damage
/// (`LivingEntity#takeKnockback`).
///
/// Follows the capability-interface pattern of {@link ActionImpairing} and {@link Synchronized}:
/// the flag lives on the `StatusEffect` and is set via {@link #configure}. Because `takeKnockback`
/// runs on a hot path, the bearer entity does **not** scan its effects per call — instead it caches
/// a single aggregate boolean (see {@link Bearer}) recomputed only when its active effects change,
/// so the check on the hot path is one field read.
public interface KnockbackImmunity {
    boolean immuneToKnockback();
    MobEffect setImmuneToKnockback(boolean value);

    static void configure(MobEffect effect) {
        configure(effect, true);
    }

    static void configure(MobEffect effect, boolean value) {
        ((KnockbackImmunity) effect).setImmuneToKnockback(value);
    }

    /// True if any of the given effects grants knockback immunity. Called only when an entity's
    /// active effects change, to refresh the cached {@link Bearer} state — never on the hot path.
    static boolean anyImmune(Collection<Holder<MobEffect>> effects) {
        for (var effect : effects) {
            if (((KnockbackImmunity) effect.value()).immuneToKnockback()) {
                return true;
            }
        }
        return false;
    }

    /// Implemented (via mixin) by the entity bearing the effects. Backed by a non-persisted boolean
    /// recomputed on effect add/removal, so it is safe to read from hot paths like `takeKnockback`.
    interface Bearer {
        boolean SpellEngine_isImmuneToKnockback();
    }

    static boolean isImmune(LivingEntity entity) {
        return ((Bearer) entity).SpellEngine_isImmuneToKnockback();
    }
}
