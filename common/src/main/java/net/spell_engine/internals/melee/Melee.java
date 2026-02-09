package net.spell_engine.internals.melee;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
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
        var world = player.getWorld();
        var hitbox = attack.hitbox();
        var arc = attack.hitbox_arc();

        // Get player orientation
        var playerPos = player.getEyePos();
        var yaw = player.getYaw();
        var pitch = player.getPitch();
        var lookVec = player.getRotationVector().normalize();

        // Create oriented bounding box for the attack hitbox
        // The hitbox is centered in front of the player based on its depth
        var hitboxCenter = playerPos.add(lookVec.multiply(hitbox.width / 2.0));

        // Create OBB with rotation from hitbox configuration
        // TODO: Handle hitbox.rotation_degrees
        var obb = new OrientedBoundingBox(
                hitboxCenter,
                hitbox.width,
                hitbox.height,
                hitbox.width, // depth = width for square hitbox
                yaw,
                pitch
        );
        obb.updateVertex();

        // Find all potential targets in a larger search area
        var searchRadius = Math.max(hitbox.width, hitbox.height) + 2.0;
        var searchBox = net.minecraft.util.math.Box.of(playerPos, searchRadius * 2, searchRadius * 2, searchRadius * 2);

        var targets = new ArrayList<Integer>();

        for (var entity : world.getOtherEntities(player, searchBox)) {
            // Skip non-living entities
            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            // Skip non-attackable entities
            if (!entity.isAttackable()) {
                continue;
            }

            // Check if entity's bounding box intersects with attack hitbox
            if (!obb.intersects(entity.getBoundingBox())) {
                continue;
            }

//            // Check arc angle if specified (0 means 360 degrees, no arc restriction)
//            if (arc > 0 && arc < 360) {
//                var toEntity = entity.getPos().subtract(playerPos).normalize();
//                var angle = Math.acos(lookVec.dotProduct(toEntity));
//                var angleDegrees = Math.toDegrees(angle);
//
//                // Check if entity is within the arc cone (half angle on each side)
//                if (angleDegrees > arc / 2.0) {
//                    continue;
//                }
//            }
            targets.add(entity.getId());
        }
        return targets;
    }

    public static void performAttackAgainstTargets(ServerPlayerEntity player, int[] targetIds) {
        var world = player.getWorld();

        var lastAttackTime = ((LivingEntityAccessor)player).spellEngine_getLastAttackedTicks();
        for (int targetId : targetIds) {
            var target = world.getEntityById(targetId);
            if (target != null && target.isAttackable()) {
                ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime - 100);
                player.attack(target);
            }
        }
        ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime);
    }
}
