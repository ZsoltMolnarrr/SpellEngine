package net.spell_engine.internals.melee;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.mixin.entity.LivingEntityAccessor;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_engine.utils.SoundHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Melee {

    public record Attack(
            int duration,
            int delay,
            float speed,
            float forward_momentum,
            Spell.Delivery.Melee.HitBox hitbox,
            PlayerAnimation animation,
            @Nullable AttackContext context
    ) {
    }
    /**
     * Context object that tracks the origin of a Melee.Attack
     * Allows mapping back from execution model to data model
     */
    public record AttackContext(
        Identifier spellId,
        String attackId
    ) {
        public static final AttackContext EMPTY = new AttackContext(Identifier.of("spell_engine", "empty"), "empty");
        /**
         * Create context for a specific attack
         */
        public static AttackContext of(Identifier spellId, String attackId) {
            return new AttackContext(spellId, attackId);
        }
    }

    public static class ActiveAttack {
        public final Attack attack;
        public final int createdAt;
        public final Item weapon;
        private boolean signaled = false;

        public ActiveAttack(Attack attack, int createdAt, Item weapon) {
            this.attack = attack;
            this.createdAt = createdAt;
            this.weapon = weapon;
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

    /**
     * Server-side: Map spell melee configuration to resolved MeleeAttack list
     * This flattens and resolves all server-side calculations (haste, etc.)
     */
    public static List<Attack> createMeleeAttacks(ServerPlayerEntity caster, Spell.Delivery.Melee meleeData,
                                                  Identifier spellId) {
        var attacks = new ArrayList<Attack>();
        var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, caster);
        for (var attack : meleeData.attacks) {
            // Calculate haste-affected duration
            var speed = (float) (attack.attack_speed_multiplier * attackSpeedMultiplier);
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
                    attack.hitbox,
                    attack.animation,
                    new AttackContext(spellId, attack.id)
            );
            attacks.add(meleeAttack);
        }

        return attacks;
    }

    @Nullable public static Spell.Delivery.Melee.Attack resolveAttackData(World world, @Nullable LivingEntity caster, @Nullable AttackContext context) {
        if (context == null) {
            return null;
        }
        return resolveAttackData(world, caster, context.spellId(), context.attackId());
    }

    @Nullable public static Spell.Delivery.Melee.Attack resolveAttackData(World world, @Nullable LivingEntity caster, Identifier spellId, String attackId) {
        var spellEntry = SpellRegistry.from(world).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return null;
        }

        var spell = spellEntry.value();
        if (spell.deliver.type == Spell.Delivery.Type.MELEE && spell.deliver.melee != null) {
            for (var attack : spell.deliver.melee.attacks) {
                if (attack.id.equals(attackId)) {
                    return attack; }
            }
        }

        return null;
    }

    public static List<Integer> detectTargets(PlayerEntity player, Attack attack) {
        var hitbox = attack.hitbox();
        var range = player.getEntityInteractionRange();
        var hitboxSize = new Vec3d(hitbox.width * range, hitbox.height * range, hitbox.width * range);
        var result = TargetFinder.findAttackTargetResult(player, null, hitboxSize, hitbox.arc, range, hitbox.rotation_roll);

        return result.entities.stream().map(Entity::getId).toList();
    }

    public static void broadcastAttackFx(ServerPlayerEntity player, AttackContext attackContext) {
        var world = player.getWorld();
        var attackData = resolveAttackData(world, player, attackContext);
        if (attackData != null) {
            var trackers = PlayerLookup.tracking(player);
            float speed = (float) (attackData.attack_speed_multiplier * AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player));
            AnimationHelper.sendAnimationExcluding(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            SoundHelper.playSound(player.getWorld(), player, attackData.sound);
            ParticleHelper.sendBatches(player, attackData.particles, 1, trackers);
        }
    }

    public static void performAttackAgainstTargets(ServerPlayerEntity player, AttackContext context, int[] targetIds) {
        var world = player.getWorld();

        var lastAttackTime = ((LivingEntityAccessor)player).spellEngine_getLastAttackedTicks();
        var targets = new ArrayList<Entity>();
        for (int targetId : targetIds) {
            var target = world.getEntityById(targetId);
            if (target != null && target.isAttackable()) {
                if (!EntityRelations.actionAllowed(
                        SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL,
                        player, target)) {
                    continue;
                }
                var timeUntilRegen = target.timeUntilRegen;
                target.timeUntilRegen = 0;
                ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(100);
                player.attack(target);
                targets.add(target);
                target.timeUntilRegen = timeUntilRegen;
            }
        }
        var spellEntry = SpellRegistry.from(world).getEntry(context.spellId());
        if (!targets.isEmpty() && spellEntry.isPresent()) {
            SpellHelper.meleeImpact(player, targets, spellEntry.get(), null);
        }
        ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime);
    }
}
