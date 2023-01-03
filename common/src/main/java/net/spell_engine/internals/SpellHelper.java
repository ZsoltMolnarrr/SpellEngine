package net.spell_engine.internals;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.Enchantments_CombatSpells;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.LivingEntityExtension;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.TargetHelper;
import net.spell_power.api.MagicSchool;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public record AmmoResult(boolean satisfied, ItemStack ammo) { }
    public static AmmoResult ammoForSpell(PlayerEntity player, Spell spell, ItemStack itemStack) {
        boolean satisfied = true;
        ItemStack ammo = null;
        boolean ignoreAmmo = player.getAbilities().creativeMode || EnchantmentHelper.getLevel(Enchantments_CombatSpells.INFINITY, itemStack) > 0;
        if (!ignoreAmmo && spell.cost.item_id != null && !spell.cost.item_id.isEmpty()) {
            var id = new Identifier(spell.cost.item_id);
            var ammoItem = Registry.ITEM.get(id);
            if(ammoItem != null) {
                ammo = ammoItem.getDefaultStack();
                satisfied = player.getInventory().contains(ammo);
            }
        }
        return new AmmoResult(satisfied, ammo);
    }

    public static float hasteAffectedValue(LivingEntity caster, float value) {
        return hasteAffectedValue(caster, value, null);
    }

    public static float hasteAffectedValue(LivingEntity caster, float value, ItemStack provisionedWeapon) {
        var haste = (float) SpellPower.getHaste(caster, provisionedWeapon);
        return value / haste;
    }

    public static float getCastDuration(LivingEntity caster, Spell spell) {
        return getCastDuration(caster, spell, null);
    }

    public static float getCastDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        if (spell.cast == null) {
            return 0;
        }
        return hasteAffectedValue(caster, spell.cast.duration, provisionedWeapon);
    }

    public static float getCastProgress(LivingEntity caster, int remainingUseTicks, Spell spell) {
        if (spell.cast == null || spell.cast.duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        var hasteAffectedDuration = getCastDuration(caster, spell);
        return Math.min(((float)elapsedTicks) / (hasteAffectedDuration * 20F), 1F);
    }

    public static float getCooldownDuration(LivingEntity caster, Spell spell) {
        return getCooldownDuration(caster, spell, null);
    }

    public static float getCooldownDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        var duration = spell.cost.cooldown_duration;
        if (duration > 0) {
            if (SpellEngineMod.config.haste_affects_cooldown) {
                duration = hasteAffectedValue(caster, spell.cost.cooldown_duration, provisionedWeapon);
            }
        }
        return duration;
    }

    public static boolean isChannelTickDue(Spell spell, int remainingUseTicks) {
        return (remainingUseTicks % spell.cast.channel_ticks) == 0;
    }

    public static boolean isChanneled(Spell spell) {
        return channelValueMultiplier(spell) != 0;
    }

    public static float channelValueMultiplier(Spell spell) {
        var ticks = spell.cast.channel_ticks;
        if (ticks <= 0) {
            return 0;
        }
        return ((float)ticks) / 20F;
    }

    public static void performSpell(World world, PlayerEntity caster, Identifier spellId, List<Entity> targets, ItemStack itemStack, SpellCastAction action, int remainingUseTicks) {
        var spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            return;
        }
        var progress = getCastProgress(caster, remainingUseTicks, spell);
        var channelMultiplier = 1F;
        boolean shouldPerformImpact = true;
        Supplier<Collection<ServerPlayerEntity>> trackingPlayers = Suppliers.memoize(() -> { // Suppliers.memoize = Lazy
            return PlayerLookup.tracking(caster);
        });
        switch (action) {
            case START -> {
                SoundHelper.playSound(caster.world, caster, spell.cast.start_sound);
                SpellCastSyncHelper.setCasting(caster, spellId, trackingPlayers.get());
                return;
            }
            case CHANNEL -> {
                channelMultiplier = channelValueMultiplier(spell);
            }
            case RELEASE -> {
                if (isChanneled(spell)) {
                    shouldPerformImpact = false;
                    channelMultiplier = 1;
                } else {
                    channelMultiplier = (progress >= 1) ? 1 : 0;
                }
                SpellCastSyncHelper.clearCasting(caster, trackingPlayers.get());
            }
        }
        var ammoResult = ammoForSpell(caster, spell, itemStack);

        if (channelMultiplier > 0 && ammoResult.satisfied()) {
            var targeting = spell.release.target;
            boolean released = action == SpellCastAction.RELEASE;
            var context = new ImpactContext(channelMultiplier, 1F, null);
            if (shouldPerformImpact) {
                switch (targeting.type) {
                    case AREA -> {
                        var center = caster.getPos().add(0, caster.getHeight() / 2F, 0);
                        var area = spell.release.target.area;
                        areaImpact(world, caster, targets, center, spell.range, area, false, spell, context);
                    }
                    case BEAM -> {
                        beamImpact(world, caster, targets, spell, context);
                    }
                    case CURSOR -> {
                        var target = targets.stream().findFirst();
                        if (target.isPresent()) {
                            directImpact(world, caster, target.get(), spell, context);
                        } else {
                            released = false;
                        }
                    }
                    case PROJECTILE -> {
                        Entity target = null;
                        var entityFound = targets.stream().findFirst();
                        if (entityFound.isPresent()) {
                            target = entityFound.get();
                        }
                        shootProjectile(world, caster, target, spell);
                    }
                    case METEOR -> {
                        var target = targets.stream().findFirst();
                        if (target.isPresent()) {
                            fallProjectile(world, caster, target.get(), spell);
                        } else {
                            released = false;
                        }
                    }
                }
            }
            if (released) {
                ParticleHelper.sendBatches(caster, spell.release.particles);
                SoundHelper.playSound(world, caster, spell.release.sound);
                AnimationHelper.sendAnimation(caster, trackingPlayers.get(), SpellAnimationType.RELEASE, spell.release.animation);
                // Consume things
                // Cooldown
                var duration = cooldownToSet(caster, spell, progress);
                if (duration > 0) {
                    ((SpellCasterEntity) caster).getCooldownManager().set(spellId, Math.round(duration * 20F));
                }
                // Exhaust
                caster.addExhaustion(spell.cost.exhaust * SpellEngineMod.config.spell_cost_exhaust_multiplier);
                // Durability
                if (SpellEngineMod.config.spell_cost_durability_allowed && spell.cost.durability > 0) {
                    itemStack.damage(spell.cost.durability, caster, (player) -> {
                        player.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND);
                        player.sendEquipmentBreakStatus(EquipmentSlot.OFFHAND);
                    });
                }
                // Item
                if (SpellEngineMod.config.spell_cost_item_allowed && ammoResult.ammo != null) {
                    for(int i = 0; i < caster.getInventory().size(); ++i) {
                        var stack = caster.getInventory().getStack(i);
                        if (stack.isOf(ammoResult.ammo.getItem())) {
                            stack.decrement(1);
                            if (stack.isEmpty()) {
                                caster.getInventory().removeOne(stack);
                            }
                            break;
                        }
                    }
                }
                // Status effect
                if (spell.cost.effect_id != null) {
                    var effect = Registry.STATUS_EFFECT.get(new Identifier(spell.cost.effect_id));
                    caster.removeStatusEffect(effect);
                }
            }
        }
    }

    private static float cooldownToSet(LivingEntity caster, Spell spell, float progress) {
        if (spell.cost.cooldown_proportional) {
            return getCooldownDuration(caster, spell) * progress;
        } else {
            return getCooldownDuration(caster, spell);
        }
    }

    private static void directImpact(World world, LivingEntity caster, Entity target, Spell spell, ImpactContext context) {
        performImpacts(world, caster, target, spell, context);
    }

    private static void beamImpact(World world, LivingEntity caster, List<Entity> targets, Spell spell, ImpactContext context) {
        for(var target: targets) {
            performImpacts(world, caster, target, spell, context);
        }
    }

    private static void areaImpact(World world, LivingEntity caster, List<Entity> targets,
                                   Vec3d center, float range, Spell.Release.Target.Area area, boolean offset,
                                   Spell spell, ImpactContext context) {
        double squaredRange = range * range;
        for(var target: targets) {
            float distanceBasedMultiplier = 1F;
            switch (area.distance_dropoff) {
                case NONE -> { }
                case SQUARED -> {
                    distanceBasedMultiplier = (float) ((squaredRange - target.squaredDistanceTo(center)) / squaredRange);
                    distanceBasedMultiplier = Math.max(distanceBasedMultiplier, 0F);
                }
            }
            performImpacts(world, caster, target, spell, context
                    .distance(distanceBasedMultiplier)
                    .offset(offset ? center : null)
            );
        }
    }

    public static void fallImpact(LivingEntity caster, Entity projectile, Spell spell, Vec3d position) {
        var info = spell.release.target.meteor;
        var area = spell.release.target.area;
        if (info == null || area == null) {
            return;
        }
        var center = position.add(0, 1, 0);
        var range = info.impact_range;
        var targets = TargetHelper.targetsFromArea(projectile, center, range, area);
        ParticleHelper.sendBatches(projectile, info.impact_particles);
        SoundHelper.playSound(projectile.world, projectile, info.impact_sound);
        areaImpact(projectile.world, caster, targets, center, range, area, true, spell, new ImpactContext());
    }

    public static float launchHeight(LivingEntity livingEntity) {
        var eyeHeight = livingEntity.getStandingEyeHeight();
        var shoulderDistance = livingEntity.getHeight() * 0.15;
        return (float) ((eyeHeight - shoulderDistance) * livingEntity.getScaleFactor());
    }

    public static Vec3d launchPoint(LivingEntity caster) {
        return launchPoint(caster, 0.5F);
    }

    public static Vec3d launchPoint(LivingEntity caster, float forward) {
        Vec3d look = caster.getRotationVector().multiply(forward * caster.getScaleFactor());
        return caster.getPos().add(0, launchHeight(caster), 0).add(look);
    }

    private static void shootProjectile(World world, LivingEntity caster, Entity target, Spell spell) {
        if (world.isClient) {
            return;
        }

        var launchPoint = launchPoint(caster);
        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FLY, spell, target);

        var projectileData = spell.release.target.projectile;
        var velocity = projectileData.velocity;
        var divergence = projectileData.divergence;
        if (projectileData.inherit_shooter_velocity) {
            projectile.setVelocity(caster, caster.getPitch(), caster.getYaw(), caster.getRoll(), velocity, divergence);
        } else {
            var look = caster.getRotationVector().normalize();
            projectile.setVelocity(look.x, look.y, look.z, velocity, divergence);
        }
        projectile.range = spell.range;
        projectile.getPitch(caster.getPitch());
        projectile.setYaw(caster.getYaw());

        world.spawnEntity(projectile);
    }

    private static void fallProjectile(World world, LivingEntity caster, Entity target, Spell spell) {
        if (world.isClient) {
            return;
        }

        var meteor = spell.release.target.meteor;
        var height = meteor.launch_height;
        var launchPoint = target.getPos().add(0, height, 0);
        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FALL, spell, target);

        var projectileData = spell.release.target.projectile;

        projectile.setVelocity(new Vec3d(0, - projectileData.velocity, 0));
        projectile.setYaw(0);
        projectile.prevYaw = projectile.getYaw();
        projectile.setPitch(-90);
        projectile.prevPitch = projectile.getPitch();
        projectile.range = height;

        world.spawnEntity(projectile);
    }

    public static boolean performImpacts(World world, LivingEntity caster, Entity target, Spell spell, ImpactContext context) {
        var performed = false;
        var trackers = PlayerLookup.tracking(target);
        for (var impact: spell.impact) {
            var result = performImpact(world, caster, target, spell.school, impact, context, trackers);
            performed = performed || result;
        }
        return performed;
    }

    public record ImpactContext(float channel, float distance, @Nullable Vec3d offset) {
        public ImpactContext() {
            this(1, 1, null);
        }

        public ImpactContext channeled(float multiplier) {
            return new ImpactContext(multiplier, distance, offset);
        }

        public ImpactContext distance(float multiplier) {
            return new ImpactContext(channel, multiplier, offset);
        }

        public ImpactContext offset(Vec3d position) {
            return new ImpactContext(channel, distance, position);
        }

        public boolean hasOffset() {
            return offset != null;
        }

        public Vec3d knockbackDirection(Vec3d targetPosition) {
            return targetPosition.subtract(offset).normalize();
        }

        public boolean isChanneled() {
            return channel != 1;
        }

        public float total() {
            return channel * distance;
        }
    }

    private static final float knockbackDefaultStrength = 0.4F;

    private static boolean performImpact(World world, LivingEntity caster, Entity target, MagicSchool school, Spell.Impact impact, ImpactContext context, Collection<ServerPlayerEntity> trackers) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        try {
            double particleMultiplier = 1 * context.total();
            var relation = TargetHelper.getRelation(caster, target);
            switch (impact.action.type) {
                case DAMAGE -> {
                    if(!TargetHelper.actionAllowed(false, relation, caster, target)) {
                        return false;
                    }
                    var damageData = impact.action.damage;
                    var knockbackMultiplier = Math.max(0F, damageData.knockback * context.total());
                    var vulnerability = SpellPower.Vulnerability.none;
                    var timeUntilRegen = target.timeUntilRegen;
                    if (target instanceof LivingEntity livingEntity) {
                        ((LivingEntityExtension) livingEntity).setKnockbackMultiplier(context.hasOffset() ? 0 : knockbackMultiplier);
                        if (SpellEngineMod.config.bypass_iframes) {
                            target.timeUntilRegen = 0;
                        }
                        vulnerability = SpellPower.getVulnerability(livingEntity, school);
                    }

                    var damage = SpellPower.getSpellDamage(school, caster);
                    var amount = damage.randomValue(vulnerability);
                    amount *= damageData.spell_power_coefficient;
                    amount *= context.total();
                    if (context.isChanneled()) {
                        amount *= SpellPower.getHaste(caster);
                    }
                    particleMultiplier = damage.criticalDamage() + vulnerability.criticalDamageBonus();

                    caster.onAttacking(target);
                    target.damage(SpellDamageSource.create(school, caster), (float) amount);

                    if (target instanceof LivingEntity livingEntity) {
                        ((LivingEntityExtension)livingEntity).setKnockbackMultiplier(1F);
                        target.timeUntilRegen = timeUntilRegen;
                        if (context.hasOffset()) {
                            var direction = context.knockbackDirection(livingEntity.getPos()).negate(); // Negate for smart Vanilla API :)
                            livingEntity.takeKnockback(knockbackDefaultStrength * knockbackMultiplier, direction.x, direction.z);
                        }
                    }
                    success = true;
                }
                case HEAL -> {
                    if(!TargetHelper.actionAllowed(true, relation, caster, target)) {
                        return false;
                    }
                    if (target instanceof LivingEntity livingTarget) {
                        var healData = impact.action.heal;
                        var healing = SpellPower.getSpellDamage(school, caster);
                        particleMultiplier = healing.criticalDamage();
                        var amount = healing.randomValue();
                        amount *= healData.spell_power_coefficient;
                        amount *= context.total();
                        if (context.isChanneled()) {
                            amount *= SpellPower.getHaste(caster);
                        }

                        livingTarget.heal((float) amount);
                        success = true;
                    }
                }
                case STATUS_EFFECT -> {
                    var data = impact.action.status_effect;
                    LivingEntity livingTarget = null;
                    if (data.apply_to_caster) {
                        livingTarget = caster;
                        relation = TargetHelper.Relation.FRIENDLY;
                    } else if (target instanceof LivingEntity livingEntity) {
                        livingTarget = livingEntity;
                    }
                    if (livingTarget != null) {
                        var id = new Identifier(data.effect_id);
                        var effect = Registry.STATUS_EFFECT.get(id);
                        if(!TargetHelper.actionAllowed(effect.isBeneficial(), relation, caster, target)) {
                            return false;
                        }
                        var duration = Math.round(data.duration * 20F);
                        // duration *= progressMultiplier; // ?????
                        var amplifier = data.amplifier;
                        switch (data.apply_mode) {
                            case SET -> {
                                livingTarget.addStatusEffect(
                                        new StatusEffectInstance(effect, duration, amplifier),
                                        caster);
                            }
                            case ADD -> {
                                var currentEffect = livingTarget.getStatusEffect(effect);
                                int newAmplifier = 0;
                                if (currentEffect != null) {
                                    var incrementedAmplifier = currentEffect.getAmplifier() + 1;
                                    newAmplifier = Math.min(incrementedAmplifier, amplifier);
                                }
                                livingTarget.addStatusEffect(
                                        new StatusEffectInstance(effect, duration, newAmplifier),
                                        caster);
                            }
                            case CONSUME -> {
                                var currentEffect = livingTarget.getStatusEffect(effect);
                                if (currentEffect != null) {
                                    var decrementedAmplifier = currentEffect.getAmplifier() - amplifier;
                                    livingTarget.removeStatusEffect(effect);
                                    if (decrementedAmplifier >= 0) {
                                        livingTarget.addStatusEffect(
                                                new StatusEffectInstance(effect, duration, decrementedAmplifier),
                                                caster);
                                    }
                                }
                            }
                        }
                        success = true;
                    }
                }
                case FIRE -> {
                    if(!TargetHelper.actionAllowed(false, relation, caster, target)) {
                        return false;
                    }
                    var data = impact.action.fire;
                    target.setOnFireFor(data.duration);
                    if (target.getFireTicks() > 0) {
                        target.setFireTicks(target.getFireTicks() + data.tick_offset);
                    }
                }
            }
            if (success) {
                if (impact.particles != null) {
                    ParticleHelper.sendBatches(target, impact.particles, (float) particleMultiplier, trackers);
                }
                if (impact.sound != null) {
                    SoundHelper.playSound(world, target, impact.sound);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to perform impact effect");
            System.err.println(e.getMessage());
            if (target instanceof LivingEntity livingEntity) {
                ((LivingEntityExtension)livingEntity).setKnockbackMultiplier(1F);
            }
        }
        return success;
    }

    // DAMAGE/HEAL OUTPUT ESTIMATION

    public static EstimatedOutput estimate(Spell spell, PlayerEntity caster, ItemStack itemStack) {
        var school = spell.school;
        var damageEffects = new ArrayList<EstimatedValue>();
        var healEffects = new ArrayList<EstimatedValue>();

        var replaceAttributes = caster.getMainHandStack() != itemStack;
        var heldAttributes = caster.getMainHandStack().getAttributeModifiers(EquipmentSlot.MAINHAND);
        var itemAttributes = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (replaceAttributes) {
            caster.getAttributes().removeModifiers(heldAttributes);
            caster.getAttributes().addTemporaryModifiers(itemAttributes);
        }

        for (var impact: spell.impact) {
            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var result = SpellPower.getSpellDamage(school, caster, itemStack);
                    var damage = new EstimatedValue(result.nonCriticalValue(), result.forcedCriticalValue())
                            .multiply(damageData.spell_power_coefficient);
                    damageEffects.add(damage);
                }
                case HEAL -> {
                    var healData = impact.action.heal;
                    var result = SpellPower.getSpellDamage(school, caster, itemStack);
                    var healing = new EstimatedValue(result.nonCriticalValue(), result.forcedCriticalValue())
                            .multiply(healData.spell_power_coefficient);
                    healEffects.add(healing);
                }
                case STATUS_EFFECT, FIRE -> {
                }
            }
        }

        if (replaceAttributes) {
            caster.getAttributes().removeModifiers(itemAttributes );
            caster.getAttributes().addTemporaryModifiers(heldAttributes);
        }

        return new EstimatedOutput(damageEffects, healEffects);
    }

    public record EstimatedValue(double min, double max) {
        public EstimatedValue multiply(double value) {
            return new EstimatedValue(min * value, max * value);
        }
    }
    public record EstimatedOutput(List<EstimatedValue> damage, List<EstimatedValue> heal) { }
}
