package net.spell_engine.api.effect;

import java.util.Collection;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/// Marks a status effect as granting immunity to knockback taken from damage
/// (`LivingEntity#knockback`).
///
/// Follows the capability-interface pattern of {@link ActionImpairing} and {@link Synchronized}:
/// the flag lives on the `StatusEffect` and is set via {@link #configure}. The bearing entity is
/// not involved — the `knockback` mixin scans the active effects at the moment of the hit.
public interface KnockbackImmunity {
    boolean immuneToKnockback();
    MobEffect setImmuneToKnockback(boolean value);

    static void configure(MobEffect effect) {
        configure(effect, true);
    }

    static void configure(MobEffect effect, boolean value) {
        ((KnockbackImmunity) effect).setImmuneToKnockback(value);
    }

    /// True if any of the given effects grants knockback immunity.
    static boolean anyImmune(Collection<Holder<MobEffect>> effects) {
        for (var effect : effects) {
            if (((KnockbackImmunity) effect.value()).immuneToKnockback()) {
                return true;
            }
        }
        return false;
    }
}
