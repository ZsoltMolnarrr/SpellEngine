package net.spell_engine.internals.melee;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellModifiers;
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
import java.util.function.Supplier;

public class Melee {

    public record Attack(
            int duration,
            int delay,
            int additional_strikes,
            int additional_strike_delay,
            boolean additional_hits_on_same_target,
            float speed,
            float forward_momentum,
            boolean allow_momentum_airborne,
            float movement_speed,
            float movement_slip,
            float range,
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


    public record CombinedAttacks(
            List<Spell.Delivery.Melee.Attack> attacks,
            List<Spell.Modifier> spellModifiers
    ) {}

    public static CombinedAttacks allAttacksOf(PlayerEntity caster, List<Spell.Delivery.Melee.Attack> meleeDataAttacks,
                                                                 RegistryEntry<Spell> spellEntry) {
        var attacks = new ArrayList<>(meleeDataAttacks);
        var modifiers = SpellModifiers.of(caster, spellEntry);
        for (var modifier: modifiers) {
            if (modifier.melee_attacks != null) {
                for (var attack : modifier.melee_attacks) {
                    attacks.add(attack);
                }
            }
        }
        return new CombinedAttacks(attacks, modifiers);
    }

    /**
     * Server-side: Map spell melee configuration to resolved MeleeAttack list
     * This flattens and resolves all server-side calculations (haste, etc.)
     */
    public static List<Attack> createMeleeAttacks(ServerPlayerEntity caster, List<Spell.Delivery.Melee.Attack> meleeDataAttacks,
                                                  RegistryEntry<Spell> spellEntry) {
        var attacks = new ArrayList<Attack>();
        var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, caster);
        var spellId = spellEntry.getKey().get().getValue();
        var allAttacks = allAttacksOf(caster, meleeDataAttacks, spellEntry);
        for (var attack : allAttacks.attacks()) {
            // Calculate haste-affected duration
            var meleeAttack = convert(caster, spellId, attack, attackSpeedMultiplier, allAttacks.spellModifiers());
            attacks.add(meleeAttack);
        }
        return attacks;
    }

    private static Attack convert(ServerPlayerEntity caster, Identifier spellId, Spell.Delivery.Melee.Attack attack, double attackSpeedMultiplier, List<Spell.Modifier> spellModifiers) {
        var speed = (float) (attack.attack_speed_multiplier * attackSpeedMultiplier);
        float duration = attack.duration > 0
                // `getAttackCooldownProgressPerTick` is poorly named, it actually returns the attack cooldown in ticks
                ? attack.duration
                : Math.max(caster.getAttackCooldownProgressPerTick() * (1F / speed), 1);
        float delay = duration * attack.delay;
        var spell = SpellRegistry.from(caster.getWorld()).getEntry(spellId);
        var range = spell.isPresent() ? SpellHelper.getRange(caster, spell.get()) : (float)caster.getEntityInteractionRange();

        var momentumBonus = 0F;
        var slipBonus = 0F;
        for (var modifier : spellModifiers) {
            momentumBonus += modifier.melee_momentum_add;
            slipBonus += modifier.melee_slipperiness_add;
        }

        // Create resolved MeleeAttack with all calculations done
        return new Attack(
                Math.round(duration),
                Math.round(delay),
                attack.additional_strikes,
                Math.max(Math.round(duration * attack.additional_strike_delay), 1),
                attack.additional_hits_on_same_target,
                speed,
                attack.forward_momentum + momentumBonus,
                attack.allow_momentum_airborne,
                attack.movement_speed,
                attack.movement_slipperiness + slipBonus,
                range,
                attack.hitbox,
                attack.animation,
                new AttackContext(spellId, attack.id)
        );
    }

    @Nullable public static Spell.Delivery.Melee.Attack resolveAttackData(PlayerEntity attacker, World world, @Nullable AttackContext context) {
        if (context == null) {
            return null;
        }
        return resolveAttackData(attacker, world, context.spellId(), context.attackId()).attack;
    }

    public record ResolutionResult(
            RegistryEntry<Spell> spell,
            Spell.Delivery.Melee melee,
            Spell.Delivery.Melee.Attack attack
    ) {}
    @Nullable public static ResolutionResult resolveAttackData(PlayerEntity attacker, World world, Identifier spellId, String attackId) {
        var spellEntry = SpellRegistry.from(world).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return null;
        }

        var spell = spellEntry.value();
        if (spell.deliver.type == Spell.Delivery.Type.MELEE && spell.deliver.melee != null) {
            var allAttacks = allAttacksOf(attacker, spell.deliver.melee.attacks, spellEntry);
            for (var attack : allAttacks.attacks()) {
                if (attack.id.equals(attackId)) {
                    return new ResolutionResult(spellEntry, spell.deliver.melee, attack);
                }
            }
        }

        return null;
    }

    public static List<Integer> detectTargets(PlayerEntity player, Attack attack) {
        var hitbox = attack.hitbox();
        var range = attack.range();
        var hitboxSize = new Vec3d(hitbox.width * range, hitbox.height * range, hitbox.length * range);
        var result = TargetFinder.findAttackTargetResult(player, null, hitboxSize, hitbox.arc, range, hitbox.roll);

        return result.entities.stream().map(Entity::getId).toList();
    }

    private static final Supplier<Boolean> REPLAY = Suppliers.memoize(() -> FabricLoader.getInstance().isModLoaded("replaymod"));

    public static void broadcastAttackFx(ServerPlayerEntity player, AttackContext attackContext) {
        var world = player.getWorld();
        var attackData = resolveAttackData(player, world, attackContext);
        if (attackData != null) {
            // Saving the attack on server side - mainly for the slipperiness
            var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player);
            var attack = convert(player, attackContext.spellId(), attackData, attackSpeedMultiplier, List.of());
            ((SpellCasterEntity) player).setMeleeSkillAttack(new ActiveAttack(attack, player.age, player.getMainHandStack().getItem()));
            // Sending fx to clients - animation, sound, particles
            var trackers = PlayerLookup.tracking(player);
            float speed = (float) (attackData.attack_speed_multiplier * AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player));
            if (REPLAY.get()) {
                AnimationHelper.sendAnimation(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            } else {
                AnimationHelper.sendAnimationExcluding(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            }
            SoundHelper.playSound(player.getWorld(), player, attackData.swing_sound);
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
            var resolvedContext = resolveAttackData(player, world, context.spellId, context.attackId);
            var spellEntry = resolvedContext.spell();
            ((SpellCasterEntity)player).setActiveMeleeSkill(spellEntry);
            var attack = resolvedContext.attack();
            Sound impactSound = null;
            int impactSoundLimit = 0;
            var modifiers = SpellModifiers.of(player, spellEntry);
            var damageMultiplierBase = 0F;
            for (var modifier : modifiers) {
                damageMultiplierBase += modifier.melee_damage_multiplier;
            }
            if (attack != null && attributeInstance != null) {
                var damageModifierAmount = attack.damage_bonus + damageMultiplierBase;
                if (damageModifierAmount != 0) {
                    appliedDamageModifier = new EntityAttributeModifier(DAMAGE_MODIFIER_ID, damageModifierAmount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    attributeInstance.addTemporaryModifier(appliedDamageModifier);
                }
                impactSound = attack.impact_sound;
                impactSoundLimit = attack.impact_sound_cap > 0 ? attack.impact_sound_cap : 999;
            }
            var attackRange = spellEntry != null ? SpellHelper.getRange(player, spellEntry) : (float)player.getEntityInteractionRange();

            for (int targetId : targetIds) {
                var target = world.getEntityById(targetId);
                if (target != null && target.isAttackable()) {
                    if (!EntityRelations.actionAllowed(
                            focusMode, SpellTarget.Intent.HARMFUL,
                            player, target)) {
                        continue;
                    }

                    var distanceGuard = (attackRange + largesSideLength(target.getBoundingBox())) * 1.2F; // Adding some tolerance
                    if (player.squaredDistanceTo(target) > (distanceGuard * distanceGuard) ) {
                        continue;
                    }

                    var timeUntilRegen = target.timeUntilRegen;
                    target.timeUntilRegen = 0;
                    ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(100);
                    player.attack(target);
                    if (impactSound != null && impactSoundLimit > 0) {
                        SoundHelper.playSound(target.getWorld(), target, impactSound);
                        impactSoundLimit -= 1;
                    }
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
        ((SpellCasterEntity)player).setActiveMeleeSkill(null);
    }

    private static SpellTarget.FocusMode focusMode() {
        return SpellEngineMod.config.melee_skills_area_focus_mode ? SpellTarget.FocusMode.AREA : SpellTarget.FocusMode.DIRECT;
    }

    private static float largesSideLength(Box boundingBox) {
        double x = boundingBox.getLengthX();
        double y = boundingBox.getLengthY();
        double z = boundingBox.getLengthZ();
        return Math.max((float)x, Math.max((float)y, (float)z));
    }
}
