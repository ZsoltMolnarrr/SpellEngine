package net.spell_engine.internals.melee;

import net.minecraft.server.network.ServerPlayerEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;

import java.util.ArrayList;
import java.util.List;

public class Melee {
    /**
     * Server-side: Map spell melee configuration to resolved MeleeAttack list
     * This flattens and resolves all server-side calculations (haste, etc.)
     */
    public static List<Attack> createMeleeAttacks(ServerPlayerEntity caster, Spell.Impact.Action.Melee meleeData) {
        var attacks = new ArrayList<Attack>();

        for (var attack : meleeData.attacks) {
            // Calculate haste-affected duration
            float duration = attack.duration_attack_speed_based
                    // `getAttackCooldownProgressPerTick` is poorly named, it actually returns the attack cooldown in ticks
                    ? Math.max(caster.getAttackCooldownProgressPerTick() * attack.duration_multiplier, 1)
                    : attack.duration_static;
            float delay = duration * attack.delay;
            // Create resolved MeleeAttack with all calculations done
            var meleeAttack = new Melee.Attack(
                    Math.round(duration),
                    Math.round(delay),
                    attack.forward_momentum,
                    attack.hitbox_arc,
                    attack.hitbox,
                    attack.animation
            );
            attacks.add(meleeAttack);
        }

        return attacks;
    }

    public record Attack(
            int duration,
            int delay,
            float forward_momentum,
            float hitbox_arc,
            Spell.Impact.Action.Melee.HitBox hitbox,
            PlayerAnimation animation
    ) {
    }

    public static class ActiveAttack {
        public final Attack attack;
        public final int createdAt;
        private boolean signaled = false;

        public ActiveAttack(Attack attack, int createdAt) {
            this.attack = attack;
            this.createdAt = createdAt;
        }

        public boolean isFinished(int currentTick) {
            return currentTick >= (createdAt + attack.duration);
        }

        public boolean isDue(int currentTick) {
            if (signaled) {
                return false;
            }
            var result = currentTick >= (createdAt + attack.delay);
            signaled = result;
            return result;
        }
    }
}
