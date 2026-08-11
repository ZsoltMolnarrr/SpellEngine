package net.spell_engine.internals.melee;
import net.spell_engine.Platform;

import com.google.common.base.Suppliers;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.fx.ModelEffectHelper;
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
import net.spell_power.api.SpellSchool;
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
        String attackId,
        /// `curve(hold ratio)` of the CHARGE cast this attack was released from, in `0..1`;
        /// always `1` for every other cast type. Travels with the attack through the client round
        /// trip, so overlapping deliveries cannot mix up each other's charge.
        ///
        /// Same unweighted value `SpellHelper.ImpactContext` carries, under the same name — the
        /// output multiplier and the charge bonus are both rebuilt from it by `resolveCharge`.
        float charge
    ) {
        public static final AttackContext EMPTY = new AttackContext(Identifier.of("spell_engine", "empty"), "empty", 1F);
        /**
         * Create context for a specific attack
         */
        public static AttackContext of(Identifier spellId, String attackId, float charge) {
            return new AttackContext(spellId, attackId, charge);
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
        return allAttacksOf(caster, meleeDataAttacks, spellEntry, null);
    }

    /// `chargeModifier` is the charge bonus scaled by the curved ratio, or null outside CHARGE casts.
    /// It never contributes `melee_attacks` (stripped in `SpellModifiers.scaledBy`), so the returned
    /// attack list — and therefore every attack id — is identical with or without it. That is what
    /// lets `resolveAttackData` map an `AttackContext` back to its attack on the return leg.
    public static CombinedAttacks allAttacksOf(PlayerEntity caster, List<Spell.Delivery.Melee.Attack> meleeDataAttacks,
                                                                 RegistryEntry<Spell> spellEntry,
                                                                 @Nullable Spell.Modifier chargeModifier) {
        var attacks = new ArrayList<>(meleeDataAttacks);
        var modifiers = SpellModifiers.of(caster, spellEntry, chargeModifier);
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
                                                  RegistryEntry<Spell> spellEntry, float curvedRatio,
                                                  @Nullable Spell.Modifier chargeModifier) {
        var attacks = new ArrayList<Attack>();
        var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, caster);
        var spellId = spellEntry.getKey().get().getValue();
        var allAttacks = allAttacksOf(caster, meleeDataAttacks, spellEntry, chargeModifier);
        for (var attack : allAttacks.attacks()) {
            // Calculate haste-affected duration
            var meleeAttack = convert(caster, spellId, attack, attackSpeedMultiplier, allAttacks.spellModifiers(), curvedRatio, chargeModifier);
            attacks.add(meleeAttack);
        }
        return attacks;
    }

    private static Attack convert(ServerPlayerEntity caster, Identifier spellId, Spell.Delivery.Melee.Attack attack, double attackSpeedMultiplier, List<Spell.Modifier> spellModifiers, float curvedRatio, @Nullable Spell.Modifier chargeModifier) {
        var speed = (float) (attack.attack_speed_multiplier * attackSpeedMultiplier);
        float duration = attack.duration > 0
                // `getAttackCooldownProgressPerTick` is poorly named, it actually returns the attack cooldown in ticks
                ? attack.duration
                : Math.max(caster.getAttackCooldownProgressPerTick() * (1F / speed), 1);
        float delay = duration * attack.delay;
        var spell = SpellRegistry.from(caster.getWorld()).getEntry(spellId);
        // Must stay in step with the server side distance guard in `performAttackAgainstTargets`,
        // which resolves the same range: a hitbox grown by `range_add` here but not there would
        // find targets the server then rejects.
        var range = spell.isPresent() ? SpellHelper.getRange(caster, spell.get(), chargeModifier) : (float)caster.getEntityInteractionRange();

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
                new AttackContext(spellId, attack.id, curvedRatio)
        );
    }

    /// Rebuilds what `SpellHelper.performSpell` resolved when the cast was released, from the curved
    /// ratio the attack carries. Both are pure functions of static spell data, so the return leg
    /// reconstructs them rather than transporting them.
    private record ResolvedCharge(float outputMultiplier, @Nullable Spell.Modifier modifier) {
        static final ResolvedCharge NONE = new ResolvedCharge(1F, null);
    }
    private static ResolvedCharge resolveCharge(@Nullable RegistryEntry<Spell> spellEntry, float curvedRatio) {
        var charge = SpellHelper.chargeConfigOf(spellEntry);
        if (charge == null) { return ResolvedCharge.NONE; }
        return new ResolvedCharge(
                SpellHelper.chargeOutputMultiplier(spellEntry, curvedRatio),
                SpellModifiers.scaledBy(charge.bonus, curvedRatio));
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
        var resolved = resolveAttackData(player, world, attackContext.spellId(), attackContext.attackId());
        var attackData = resolved != null ? resolved.attack() : null;
        if (attackData != null) {
            // Saving the attack on server side - mainly for the slipperiness
            var attackSpeedMultiplier = AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player);
            var curvedRatio = MathHelper.clamp(attackContext.charge(), 0F, 1F); // Client supplied
            var charge = resolveCharge(resolved.spell(), curvedRatio);
            // The full modifier list (not `List.of()`): the slipperiness stored here drives server
            // side movement, so it has to match the value the client is already sliding with.
            var modifiers = allAttacksOf(player, resolved.melee().attacks, resolved.spell(), charge.modifier()).spellModifiers();
            var attack = convert(player, attackContext.spellId(), attackData, attackSpeedMultiplier, modifiers, curvedRatio, charge.modifier());
            ((SpellCasterEntity) player).setMeleeSkillAttack(new ActiveAttack(attack, player.age, player.getMainHandStack().getItem()));
            // Sending fx to clients - animation, sound, particles
            var trackers = Platform.tracking(player);
            float speed = (float) (attackData.attack_speed_multiplier * AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, player));
            if (REPLAY.get()) {
                AnimationHelper.sendAnimation(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            } else {
                AnimationHelper.sendAnimationExcluding(player, trackers, SpellCast.Animation.RELEASE, attackData.animation, speed);
            }
            SoundHelper.playSound(player.getWorld(), player, attackData.swing_sound);
            var swingVisuals = attackData.visuals.resolved(Fx.Context.NONE);
            ParticleHelper.sendBatches(player, swingVisuals.particles, 1, trackers);
            ModelEffectHelper.spawn(player.getWorld(), player.getPos(), player.getYaw(), swingVisuals.models, player);
        }
    }

    /// Melee skills land their damage through vanilla `player.attack(...)`, which reads
    /// `GENERIC_ATTACK_DAMAGE` — an attribute only the main hand contributes to. A spell of the
    /// dual wielding school swings both weapons, so the power difference between the two schools is
    /// expressed here as a damage multiplier over the single handed school.
    ///
    /// Returns the bonus fraction (`0` for every other school), not the multiplier itself, ready to
    /// be handed to an `ADD_MULTIPLIED_TOTAL` modifier.
    private static float dualWieldDamageBonus(PlayerEntity player, @Nullable Spell spell) {
        if (spell == null || spell.school != ExternalSpellSchools.PHYSICAL_MELEE_DUAL) {
            return 0F;
        }
        var query = new SpellSchool.QueryArgs(player);
        var singleHanded = ExternalSpellSchools.PHYSICAL_MELEE.getValue(SpellSchool.Trait.POWER, query);
        if (singleHanded <= 0) {
            return 0F;
        }
        var dualWielded = ExternalSpellSchools.PHYSICAL_MELEE_DUAL.getValue(SpellSchool.Trait.POWER, query);
        return (float) (dualWielded / singleHanded) - 1F;
    }

    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.of(SpellEngineMod.ID, "melee_attack");
    private static final Identifier DUAL_WIELD_MODIFIER_ID = Identifier.of(SpellEngineMod.ID, "melee_attack_dual_wield");
    private static final Identifier CHARGE_MODIFIER_ID = Identifier.of(SpellEngineMod.ID, "melee_attack_charge");
    public static void performAttackAgainstTargets(ServerPlayerEntity player, AttackContext context, int[] targetIds) {
        var world = player.getWorld();
        var focusMode = focusMode();
        var attributeInstance = player.getAttributes().getCustomInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        EntityAttributeModifier appliedDamageModifier = null;
        EntityAttributeModifier appliedDualWieldModifier = null;
        EntityAttributeModifier appliedChargeModifier = null;
        // Arrived from the client, so it is never trusted as-is.
        var curvedRatio = MathHelper.clamp(context.charge(), 0F, 1F);
        try {
            var lastAttackTime = ((LivingEntityAccessor)player).spellEngine_getLastAttackedTicks();
            var targets = new ArrayList<Entity>();
            var resolvedContext = resolveAttackData(player, world, context.spellId, context.attackId);
            var spellEntry = resolvedContext.spell();
            ((SpellCasterEntity)player).setActiveMeleeSkill(spellEntry);
            var attack = resolvedContext.attack();
            Sound impactSound = null;
            int impactSoundLimit = 0;
            var resolvedCharge = resolveCharge(spellEntry, curvedRatio);
            var charge = resolvedCharge.outputMultiplier();
            var modifiers = SpellModifiers.of(player, spellEntry, resolvedCharge.modifier());
            var damageMultiplierBase = 0F;
            for (var modifier : modifiers) {
                damageMultiplierBase += modifier.melee_damage_multiplier;
            }
            // Read before any temporary modifier lands, so it cannot inflate its own ratio. Kept as a
            // separate modifier rather than summed into the one below: `ADD_MULTIPLIED_TOTAL`
            // modifiers compose multiplicatively, so this way percentage bonuses scale the dual
            // wielded swing instead of the main hand one.
            var dualWieldBonus = dualWieldDamageBonus(player, spellEntry != null ? spellEntry.value() : null);
            if (dualWieldBonus != 0 && attributeInstance != null) {
                appliedDualWieldModifier = new EntityAttributeModifier(DUAL_WIELD_MODIFIER_ID, dualWieldBonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                attributeInstance.addTemporaryModifier(appliedDualWieldModifier);
            }
            // Melee skills land their damage through vanilla `player.attack(...)`, which the
            // `ImpactContext` never reaches, so a partial CHARGE release scales the swing here.
            // Kept separate for the same reason as the dual wield bonus: `ADD_MULTIPLIED_TOTAL`
            // modifiers compose multiplicatively, so the charge scales the whole swing rather
            // than being diluted by the flat bonuses below.
            if (charge != 1F && attributeInstance != null) {
                appliedChargeModifier = new EntityAttributeModifier(CHARGE_MODIFIER_ID, charge - 1F, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                attributeInstance.addTemporaryModifier(appliedChargeModifier);
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
            var attackRange = spellEntry != null ? SpellHelper.getRange(player, spellEntry, resolvedCharge.modifier()) : (float)player.getEntityInteractionRange();

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
                        .position(player.getPos())
                        .charge(curvedRatio)
                        .chargeModifier(resolvedCharge.modifier());
                SpellHelper.meleeImpact(player, targets, spellEntry, impactContext);
            }
            ((LivingEntityAccessor)player).spellEngine_setLastAttackedTicks(lastAttackTime);
        } catch (Exception e) {
            System.err.println("Failed to perform melee attack: " + e.getMessage());
        }
        if (appliedDamageModifier != null) {
            attributeInstance.removeModifier(appliedDamageModifier);
        }
        if (appliedDualWieldModifier != null) {
            attributeInstance.removeModifier(appliedDualWieldModifier);
        }
        if (appliedChargeModifier != null) {
            attributeInstance.removeModifier(appliedChargeModifier);
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
