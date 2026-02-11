package net.spell_engine.internals.melee;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.mixin.entity.LivingEntityAccessor;

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
            var speed = attack.attack_speed_multiplier;
            float duration = attack.duration_attack_speed_based
                    // `getAttackCooldownProgressPerTick` is poorly named, it actually returns the attack cooldown in ticks
                    ? Math.max(caster.getAttackCooldownProgressPerTick() * (1F / speed), 1)
                    : attack.duration_static;
            float delay = duration * attack.delay;
            // Create resolved MeleeAttack with all calculations done
            var meleeAttack = new Melee.Attack(
                    Math.round(duration),
                    Math.round(delay),
                    speed,
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
            float speed,
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

    public static List<Integer> detectTargets(PlayerEntity player, Attack attack) {
        var hitbox = attack.hitbox();
        var range = player.getEntityInteractionRange();
        var hitboxSize = new Vec3d(hitbox.width * range, hitbox.height * range, hitbox.width * range);
        var result = TargetFinder.findAttackTargetResult(player, null, hitboxSize, attack.hitbox_arc, range);

        return result.entities.stream().map(Entity::getId).toList();
    }

    public static void performAttackAgainstTargets(ServerPlayerEntity player, int[] targetIds) {
        var world = player.getWorld();

        var lastAttackTime = ((LivingEntityAccessor)player).spellEngine_getLastAttackedTicks();
        for (int targetId : targetIds) {
            var target = world.getEntityById(targetId);
            if (target != null && target.isAttackable()) {
                var timeUntilRegen = target.timeUntilRegen;
                target.timeUntilRegen = 0;
                ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(100);
                player.attack(target);
                target.timeUntilRegen = timeUntilRegen;
            }
        }
        ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime);
    }
}
