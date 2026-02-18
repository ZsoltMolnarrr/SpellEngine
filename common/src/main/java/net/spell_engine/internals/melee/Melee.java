package net.spell_engine.internals.melee;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.mixin.entity.LivingEntityAccessor;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_engine.utils.SoundHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Melee {

    public record Attack(
            int duration,
            int delay,
            int additional_strikes,
            int additional_strike_delay,
            boolean additional_hits_on_same_target,
            float speed,
            float forward_momentum,
            float movement_speed,
            float movement_slip,
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
        public final Set<Integer> hitEntityIds = new HashSet<>();
        private final ArrayList<Integer> hitTicks;

        public ActiveAttack(Attack attack, int createdAt, Item weapon) {
            this.attack = attack;
            this.createdAt = createdAt;
            this.weapon = weapon;
            var ticks = new ArrayList<Integer>();
            var firstHit = createdAt + attack.delay;
            ticks.add(firstHit);
            for (int i = 1; i <= attack.additional_strikes; i++) {
                ticks.add(firstHit + (i * attack.additional_strike_delay));
            }
            this.hitTicks = ticks;
        }

        public boolean isFinished(int currentTick) {
            return currentTick >= (createdAt + attack.duration) && currentTick >= hitTicks.getLast();
        }

        public boolean isDue(int currentTick) {
            return hitTicks.contains(currentTick);
        }
    }

    /**
     * Server-side: Map spell melee configuration to resolved MeleeAttack list
     * This flattens and resolves all server-side calculations (haste, etc.)
     */
    public static List<Attack> createMeleeAttacks(ServerPlayerEntity caster, List<Spell.Delivery.Melee.Attack> meleeDataAttacks,
                                                  Identifier spellId) {
        var attacks = new ArrayList<Attack>();
        var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, caster);
        for (var attack : meleeDataAttacks) {
            // Calculate haste-affected duration
            var meleeAttack = convert(caster, spellId, attack, attackSpeedMultiplier);
            attacks.add(meleeAttack);
        }

        return attacks;
    }

    private static Attack convert(ServerPlayerEntity caster, Identifier spellId, Spell.Delivery.Melee.Attack attack, double attackSpeedMultiplier) {
        var speed = (float) (attack.attack_speed_multiplier * attackSpeedMultiplier);
        float duration = attack.duration > 0
                // `getAttackCooldownProgressPerTick` is poorly named, it actually returns the attack cooldown in ticks
                ? attack.duration
                : Math.max(caster.getAttackCooldownProgressPerTick() * (1F / speed), 1);
        float delay = duration * attack.delay;
        // Create resolved MeleeAttack with all calculations done
        return new Attack(
                Math.round(duration),
                Math.round(delay),
                attack.additional_strikes,
                Math.max(Math.round(duration * attack.additional_strike_delay), 1),
                attack.additional_hits_on_same_target,
                speed,
                attack.forward_momentum,
                attack.movement_speed,
                attack.movement_slipperiness,
                attack.hitbox,
                attack.animation,
                new AttackContext(spellId, attack.id)
        );
    }

    @Nullable public static Spell.Delivery.Melee.Attack resolveAttackData(World world, @Nullable AttackContext context) {
        if (context == null) {
            return null;
        }
        return resolveAttackData(world, context.spellId(), context.attackId()).attack;
    }

    public record ResolutionResult(
            RegistryEntry<Spell> spell,
            Spell.Delivery.Melee melee,
            Spell.Delivery.Melee.Attack attack
    ) {}
    @Nullable public static ResolutionResult resolveAttackData(World world, Identifier spellId, String attackId) {
        var spellEntry = SpellRegistry.from(world).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return null;
        }

        var spell = spellEntry.value();
        if (spell.deliver.type == Spell.Delivery.Type.MELEE && spell.deliver.melee != null) {
            for (var attack : spell.deliver.melee.attacks) {
                if (attack.id.equals(attackId)) {
                    return new ResolutionResult(spellEntry, spell.deliver.melee, attack);
                }
            }
        }

        return null;
    }

    public static List<Integer> detectTargets(PlayerEntity player, Attack attack) {
        var hitbox = attack.hitbox();
        var range = player.getEntityInteractionRange();
        var hitboxSize = new Vec3d(hitbox.width * range, hitbox.height * range, hitbox.length * range);
        var result = TargetFinder.findAttackTargetResult(player, null, hitboxSize, hitbox.arc, range, hitbox.roll);

        return result.entities.stream().map(Entity::getId).toList();
    }

    public static void broadcastAttackFx(ServerPlayerEntity player, AttackContext attackContext) {
        var world = player.getWorld();
        var attackData = resolveAttackData(world, attackContext);
        if (attackData != null) {
            // Saving the attack on server side - mainly for the slipperiness
            var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player);
            var attack = convert(player, attackContext.spellId(), attackData, attackSpeedMultiplier);
            ((SpellCasterEntity) player).setMeleeSkillAttack(new ActiveAttack(attack, player.age, player.getMainHandStack().getItem()));
            // Sending fx to clients - animation, sound, particles
            var trackers = PlayerLookup.tracking(player);
            float speed = (float) (attackData.attack_speed_multiplier * AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player));
            AnimationHelper.sendAnimationExcluding(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            SoundHelper.playSound(player.getWorld(), player, attackData.sound);
            ParticleHelper.sendBatches(player, attackData.particles, 1, trackers);
        }
    }

    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.of(SpellEngineMod.ID, "melee_attack");
    public static void performAttackAgainstTargets(ServerPlayerEntity player, AttackContext context, int[] targetIds) {
        var world = player.getWorld();
        var focusMode = focusMode();
        var attributeInstance = player.getAttributes().getCustomInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        EntityAttributeModifier appliedDamageModifier = null;
        try {
            var lastAttackTime = ((LivingEntityAccessor)player).spellEngine_getLastAttackedTicks();
            var targets = new ArrayList<Entity>();
            var resolvedContext = resolveAttackData(world, context.spellId, context.attackId);
            var spellEntry = resolvedContext.spell();
            var attack = resolvedContext.attack();
            if (attack != null && attributeInstance != null) {
                var damageModifierAmount = attack.damage_bonus;
                if (damageModifierAmount != 0) {
                    appliedDamageModifier = new EntityAttributeModifier(DAMAGE_MODIFIER_ID, damageModifierAmount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    attributeInstance.addTemporaryModifier(appliedDamageModifier);
                }
            }

            for (int targetId : targetIds) {
                var target = world.getEntityById(targetId);
                if (target != null && target.isAttackable()) {
                    if (!EntityRelations.actionAllowed(
                            focusMode, SpellTarget.Intent.HARMFUL,
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

            if (!targets.isEmpty()) {
                var impactContext = new SpellHelper.ImpactContext()
                        .position(player.getPos());
                SpellHelper.meleeImpact(player, targets, spellEntry, impactContext);
            }
            ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime);
        } catch (Exception e) {
            System.err.println("Failed to perform melee attack: " + e.getMessage());
        }
        if (appliedDamageModifier != null) {
            attributeInstance.removeModifier(appliedDamageModifier);
        }
    }

    private static SpellTarget.FocusMode focusMode() {
        return SpellEngineMod.config.melee_skills_area_focus_mode ? SpellTarget.FocusMode.AREA : SpellTarget.FocusMode.DIRECT;
    }
}
