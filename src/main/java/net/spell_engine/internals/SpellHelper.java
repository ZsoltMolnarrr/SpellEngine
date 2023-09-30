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
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.enchantment.Enchantments_SpellEngine;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.CustomSpellHandler;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellEvents;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.TargetHelper;
import net.spell_power.api.MagicSchool;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class SpellHelper {
    public static int maximumUseTicks = 72000; // 72000 ticks = 1 hour

    public static SpellCast.Attempt attemptCasting(PlayerEntity player, ItemStack itemStack, Identifier spellId) {
        return attemptCasting(player, itemStack, spellId, true);
    }

    public static SpellCast.Attempt attemptCasting(PlayerEntity player, ItemStack itemStack, Identifier spellId, boolean checkAmmo) {
        var caster = (SpellCasterEntity)player;
        var spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            return SpellCast.Attempt.none();
        }
        if (caster.getCooldownManager().isCoolingDown(spellId)) {
            return SpellCast.Attempt.failOnCooldown(new SpellCast.Attempt.OnCooldownInfo());
        }
        if (checkAmmo) {
            var ammoResult = SpellHelper.ammoForSpell(player, spell, itemStack);
            if (!ammoResult.satisfied()) {
                return SpellCast.Attempt.failMissingItem(new SpellCast.Attempt.MissingItemInfo(ammoResult.ammo.getItem()));
            }
        }
        return SpellCast.Attempt.success();
    }

    public record AmmoResult(boolean satisfied, ItemStack ammo) { }
    public static AmmoResult ammoForSpell(PlayerEntity player, Spell spell, ItemStack itemStack) {
        boolean satisfied = true;
        ItemStack ammo = null;
        boolean ignoreAmmo = player.getAbilities().creativeMode
                || EnchantmentHelper.getLevel(Enchantments_SpellEngine.INFINITY, itemStack) > 0
                || !SpellEngineMod.config.spell_cost_item_allowed;
        if (!ignoreAmmo && spell.cost.item_id != null && !spell.cost.item_id.isEmpty()) {
            var id = new Identifier(spell.cost.item_id);
            var ammoItem = Registries.ITEM.get(id);
            if(ammoItem != null) {
                ammo = ammoItem.getDefaultStack();
                satisfied = player.getInventory().contains(ammo);
            }
        }
        return new AmmoResult(satisfied, ammo);
    }

    public static float hasteAffectedValue(float value, float haste) {
        return value / haste;
    }

    public static float hasteAffectedValue(LivingEntity caster, float value) {
        return hasteAffectedValue(caster, value, null);
    }

    public static float hasteAffectedValue(LivingEntity caster, float value, ItemStack provisionedWeapon) {
        var haste = (float) SpellPower.getHaste(caster, provisionedWeapon);
        return hasteAffectedValue(value, haste);
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

    public static SpellCast.Duration getCastTimeDetails(LivingEntity caster, Spell spell) {
        var haste = (float) SpellPower.getHaste(caster, null);
        var duration = hasteAffectedValue(spell.cast.duration, haste);
        return new SpellCast.Duration(haste, Math.round(duration * 20F));
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
        var offset = Math.round(spell.cast.channel_ticks * 0.5F);
        var currentTick = maximumUseTicks - remainingUseTicks + offset;
        return currentTick >= spell.cast.channel_ticks
                && (currentTick % spell.cast.channel_ticks) == 0;
    }

    public static boolean isChanneled(Spell spell) {
        return channelValueMultiplier(spell) != 0;
    }

    public static boolean isInstant(Spell spell) {
        return spell.cast.duration == 0;
    }

    public static float channelValueMultiplier(Spell spell) {
        var ticks = spell.cast.channel_ticks;
        if (ticks <= 0) {
            return 0;
        }
        return ((float)ticks) / 20F;
    }

    public static void startCasting(PlayerEntity player, Identifier spellId, float speed, int length) {
        var spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            return;
        }
        var itemStack = player.getMainHandStack();
        var attempt = attemptCasting(player, itemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        var process = new SpellCast.Process(spellId, spell, itemStack, speed, length);
        SpellCastSyncHelper.setCasting(player, process);
        SoundHelper.playSound(player.getWorld(), player, spell.cast.start_sound);
    }

    public static void performSpell(World world, PlayerEntity player, Identifier spellId, List<Entity> targets, ItemStack itemStack, SpellCast.Action action, Hand hand, float progress) {
        var spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            return;
        }
        var spellInfo = new SpellInfo(spell, spellId);
        var caster = (SpellCasterEntity)player;
        if (caster.getCooldownManager().isCoolingDown(spellId)) {
            return;
        }
        // Normalized progress in 0 to 1
        progress = Math.max(Math.min(progress, 1F), 0F);
        var channelMultiplier = 1F;
        boolean shouldPerformImpact = true;
        Supplier<Collection<ServerPlayerEntity>> trackingPlayers = Suppliers.memoize(() -> { // Suppliers.memoize = Lazy
            return PlayerLookup.tracking(player);
        });
        switch (action) {
            case START -> {
                // TODO: Remove
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
                SpellCastSyncHelper.clearCasting(player);
            }
        }
        var ammoResult = ammoForSpell(player, spell, itemStack);

        if (channelMultiplier > 0 && ammoResult.satisfied()) {
            var targeting = spell.release.target;
            boolean released = action == SpellCast.Action.RELEASE;
            if (shouldPerformImpact) {
                var context = new ImpactContext(channelMultiplier,
                        1F,
                        null,
                        SpellPower.getSpellPower(spell.school, player),
                        impactTargetingMode(spell));
                if (spell.release.custom_impact) {
                    var handler = CustomSpellHandler.handlers.get(spellId);
                    released = false;
                    if (handler != null) {
                        released = handler.apply(new CustomSpellHandler.Data(
                                player, targets, itemStack, action, hand, progress, context));
                    }
                } else {
                    switch (targeting.type) {
                        case AREA -> {
                            var center = player.getPos().add(0, player.getHeight() / 2F, 0);
                            var area = spell.release.target.area;
                            areaImpact(world, player, targets, center, spell.range, area, false, spell, context);
                        }
                        case BEAM -> {
                            beamImpact(world, player, targets, spell, context);
                        }
                        case CURSOR -> {
                            var target = targets.stream().findFirst();
                            if (target.isPresent()) {
                                directImpact(world, player, target.get(), spell, context);
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
                            shootProjectile(world, player, target, spellInfo, context);
                        }
                        case METEOR -> {
                            var target = targets.stream().findFirst();
                            if (target.isPresent()) {
                                fallProjectile(world, player, target.get(), spellInfo, context);
                            } else {
                                released = false;
                            }
                        }
                        case SELF -> {
                            directImpact(world, player, player, spell, context);
                            released = true;
                        }
                    }
                }
            }
            if (released) {
                ParticleHelper.sendBatches(player, spell.release.particles);
                SoundHelper.playSound(world, player, spell.release.sound);
                AnimationHelper.sendAnimation(player, trackingPlayers.get(), SpellCast.Animation.RELEASE, spell.release.animation);
                // Consume things
                // Cooldown
                var duration = cooldownToSet(player, spell, progress);
                if (duration > 0) {
                    ((SpellCasterEntity) player).getCooldownManager().set(spellId, Math.round(duration * 20F));
                }
                // Exhaust
                player.addExhaustion(spell.cost.exhaust * SpellEngineMod.config.spell_cost_exhaust_multiplier);
                // Durability
                if (SpellEngineMod.config.spell_cost_durability_allowed && spell.cost.durability > 0) {
                    itemStack.damage(spell.cost.durability, player, (playerObj) -> {
                        playerObj.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND);
                        playerObj.sendEquipmentBreakStatus(EquipmentSlot.OFFHAND);
                    });
                }
                // Item
                if (ammoResult.ammo != null) {
                    for(int i = 0; i < player.getInventory().size(); ++i) {
                        var stack = player.getInventory().getStack(i);
                        if (stack.isOf(ammoResult.ammo.getItem())) {
                            stack.decrement(1);
                            if (stack.isEmpty()) {
                                player.getInventory().removeOne(stack);
                            }
                            break;
                        }
                    }
                }
                // Status effect
                if (spell.cost.effect_id != null) {
                    var effect = Registries.STATUS_EFFECT.get(new Identifier(spell.cost.effect_id));
                    player.removeStatusEffect(effect);
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
                    .position(offset ? center : null)
            );
        }
    }

    public static void fallImpact(LivingEntity caster, Entity projectile, Spell spell, ImpactContext context) {
        var adjustedCenter = context.position().add(0, 1, 0); // Adding a bit of height to avoid raycast hitting the ground
        performProjectileAreaEffect(caster, null, projectile, spell, context.position(adjustedCenter));
    }

    public static boolean projectileImpact(LivingEntity caster, Entity projectile, Entity target, Spell spell, ImpactContext context) {
        var performed = performImpacts(projectile.getWorld(), caster, target, spell, context);

        if (performed) {
            performProjectileAreaEffect(caster, target, projectile, spell, context);
        }

        return performed;
    }

    private static void performProjectileAreaEffect(LivingEntity caster, Entity previouslyHit, Entity projectile, Spell spell, ImpactContext context) {
        var projectileData = spell.release.target.projectile;
        if (projectileData != null) {
            var area_impact = projectileData.area_impact;
            if (area_impact != null) {
                var center = context.position();
                var targets = TargetHelper.targetsFromArea(projectile, center, area_impact.radius, area_impact.area, null);
                if (previouslyHit != null) {
                    targets.remove(previouslyHit);
                }
                areaImpact(projectile.getWorld(), caster, targets, center, area_impact.radius, area_impact.area, true, spell, context.target(TargetHelper.TargetingMode.AREA));
                ParticleHelper.sendBatches(projectile, area_impact.particles);
                SoundHelper.playSound(projectile.getWorld(), projectile, area_impact.sound);
            }
        }
    }

    public static float launchHeight(LivingEntity livingEntity) {
        var eyeHeight = livingEntity.getStandingEyeHeight();
        var shoulderDistance = livingEntity.getHeight() * 0.15;
        return (float) ((eyeHeight - shoulderDistance) * livingEntity.getScaleFactor());
    }

    public static Vec3d launchPoint(LivingEntity caster) {
        return launchPoint(caster, launchPointOffsetDefault);
    }

    public static float launchPointOffsetDefault = 0.5F;

    public static Vec3d launchPoint(LivingEntity caster, float forward) {
        Vec3d look = caster.getRotationVector().multiply(forward * caster.getScaleFactor());
        return caster.getPos().add(0, launchHeight(caster), 0).add(look);
    }

    public static void shootProjectile(World world, LivingEntity caster, Entity target, SpellInfo spellInfo, ImpactContext context) {
        shootProjectile(world, caster, target, spellInfo, context, true);
    }

    public static void shootProjectile(World world, LivingEntity caster, Entity target, SpellInfo spellInfo, ImpactContext context, boolean initial) {
        if (world.isClient) {
            return;
        }

        var spell = spellInfo.spell();
        var launchPoint = launchPoint(caster);
        var projectileData = spell.release.target.projectile;
        var mutablePerks = projectileData.perks.copy();

        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FLY, spell, target, context, mutablePerks);

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


        if (SpellEvents.PROJECTILE_SHOOT.isListened()) {
            SpellEvents.PROJECTILE_SHOOT.invoke((listener) -> listener.onProjectileLaunch(new SpellEvents.ProjectileLaunchEvent(projectile, caster, target, spellInfo, context, initial)));
        }
        world.spawnEntity(projectile);

        if (initial && projectile.mutablePerks().extra_launch_count > 0) {
            for (int i = 0; i < projectile.mutablePerks().extra_launch_count; i++) {
                var ticks = (i + 1) * projectile.mutablePerks().extra_launch_delay;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    shootProjectile(world, caster, target, spellInfo, context, false);
                });
            }
        }
    }

    public static void fallProjectile(World world, LivingEntity caster, Entity target, SpellInfo spellInfo, ImpactContext context) {
        fallProjectile(world, caster, target, spellInfo, context, true);
    }

    public static void fallProjectile(World world, LivingEntity caster, Entity target, SpellInfo spellInfo, ImpactContext context, boolean initial) {
        if (world.isClient) {
            return;
        }

        var spell = spellInfo.spell();
        var meteor = spell.release.target.meteor;
        var height = meteor.launch_height;
        var launchPoint = target.getPos().add(0, height, 0);
        var projectileData = spell.release.target.projectile;
        var mutablePerks = projectileData.perks.copy();

        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FALL, spell, target, context, mutablePerks);

        projectile.setYaw(0);
        projectile.setPitch(90);
        if (!initial) {
            projectile.setVelocity( 0, - 1, 0, projectileData.velocity, 0.5F, projectileData.divergence);
            projectile.setFollowedTarget(null);
        } else {
            projectile.setVelocity(new Vec3d(0, - projectileData.velocity, 0));
        }

        projectile.prevYaw = projectile.getYaw();
        projectile.prevPitch = projectile.getPitch();
        projectile.range = height;

        if (SpellEvents.PROJECTILE_FALL.isListened()) {
            SpellEvents.PROJECTILE_FALL.invoke((listener) -> listener.onProjectileLaunch(new SpellEvents.ProjectileLaunchEvent(projectile, caster, target, spellInfo, context, initial)));
        }
        world.spawnEntity(projectile);

        if (initial && projectile.mutablePerks().extra_launch_count > 0) {
            for (int i = 0; i < projectile.mutablePerks().extra_launch_count; i++) {
                var ticks = (i + 1) * projectile.mutablePerks().extra_launch_delay;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    fallProjectile(world, caster, target, spellInfo, context, false);
                });
            }
        }
    }

    public static boolean performImpacts(World world, LivingEntity caster, Entity target, Spell spell, ImpactContext context) {
        var performed = false;
        var trackers = PlayerLookup.tracking(target);

        TargetHelper.Intent selectedIntent = null;
        for (var impact: spell.impact) {
            var intent = intent(impact.action);
            if (!impact.action.apply_to_caster // Only filtering for cases when another entity is actually targeted
                    && (selectedIntent != null && selectedIntent != intent)) {
                // Filter out mixed intents
                // So dual intent spells either damage or heal, and not do both
                continue;
            }
            var result = performImpact(world, caster, target, spell.school, impact, context, trackers);
            performed = performed || result;
            if (result) {
                selectedIntent = intent;
            }
        }
        return performed;
    }

    public record ImpactContext(float channel, float distance, @Nullable Vec3d position, SpellPower.Result power, TargetHelper.TargetingMode targetingMode) {
        public ImpactContext() {
            this(1, 1, null, null, TargetHelper.TargetingMode.DIRECT);
        }

        public ImpactContext channeled(float multiplier) {
            return new ImpactContext(multiplier, distance, position, power, targetingMode);
        }

        public ImpactContext distance(float multiplier) {
            return new ImpactContext(channel, multiplier, position, power, targetingMode);
        }

        public ImpactContext position(Vec3d position) {
            return new ImpactContext(channel, distance, position, power, targetingMode);
        }

        public ImpactContext power(SpellPower.Result spellPower) {
            return new ImpactContext(channel, distance, position, spellPower, targetingMode);
        }

        public ImpactContext target(TargetHelper.TargetingMode targetingMode) {
            return new ImpactContext(channel, distance, position, power, targetingMode);
        }

        public boolean hasOffset() {
            return position != null;
        }

        public Vec3d knockbackDirection(Vec3d targetPosition) {
            return targetPosition.subtract(position).normalize();
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
            var power = context.power();
            if (power == null) {
                power = SpellPower.getSpellPower(school, caster);
            }
            if (power.baseValue() < impact.action.min_power) {
                power = new SpellPower.Result(power.school(), impact.action.min_power, power.criticalChance(), power.criticalDamage());
            }

            if (impact.action.apply_to_caster) {
                target = caster;
            }

            if (!TargetHelper.actionAllowed(context.targetingMode(), intent(impact.action), caster, target)) {
                return false;
            }

            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var knockbackMultiplier = Math.max(0F, damageData.knockback * context.total());
                    var vulnerability = SpellPower.Vulnerability.none;
                    var timeUntilRegen = target.timeUntilRegen;
                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback) livingEntity).setKnockbackMultiplier_SpellEngine(context.hasOffset() ? 0 : knockbackMultiplier);
                        if (SpellEngineMod.config.bypass_iframes) {
                            target.timeUntilRegen = 0;
                        }
                        vulnerability = SpellPower.getVulnerability(livingEntity, school);
                    }
                    var amount = power.randomValue(vulnerability);
                    amount *= damageData.spell_power_coefficient;
                    amount *= context.total();
                    if (context.isChanneled()) {
                        amount *= SpellPower.getHaste(caster);
                    }
                    particleMultiplier = power.criticalDamage() + vulnerability.criticalDamageBonus();

                    caster.onAttacking(target);
                    target.damage(SpellDamageSource.create(school, caster), (float) amount);

                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback)livingEntity).setKnockbackMultiplier_SpellEngine(1F);
                        target.timeUntilRegen = timeUntilRegen;
                        if (context.hasOffset()) {
                            var direction = context.knockbackDirection(livingEntity.getPos()).negate(); // Negate for smart Vanilla API :)
                            livingEntity.takeKnockback(knockbackDefaultStrength * knockbackMultiplier, direction.x, direction.z);
                        }
                    }
                    success = true;
                }
                case HEAL -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var healData = impact.action.heal;
                        particleMultiplier = power.criticalDamage();
                        var amount = power.randomValue();
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
                    if (target instanceof LivingEntity livingTarget) {
                        var id = new Identifier(data.effect_id);
                        var effect = Registries.STATUS_EFFECT.get(id);
                        if(!underApplyLimit(power, livingTarget, school, data.apply_limit)) {
                            return false;
                        }
                        var duration = Math.round(data.duration * 20F);
                        // duration *= progressMultiplier; // ?????
                        var amplifier = data.amplifier + (int)(data.amplifier_power_multiplier * power.nonCriticalValue());
                        var showParticles = data.show_particles;
                        switch (data.apply_mode) {
                            case SET -> {
                            }
                            case ADD -> {
                                var currentEffect = livingTarget.getStatusEffect(effect);
                                int newAmplifier = 0;
                                if (currentEffect != null) {
                                    var incrementedAmplifier = currentEffect.getAmplifier() + 1;
                                    newAmplifier = Math.min(incrementedAmplifier, amplifier);
                                }
                                amplifier = newAmplifier;
                            }
                        }
                        livingTarget.addStatusEffect(
                                new StatusEffectInstance(effect, duration, amplifier, false, showParticles, true),
                                caster);
                        success = true;
                    }
                }
                case FIRE -> {
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
                ((ConfigurableKnockback)livingEntity).setKnockbackMultiplier_SpellEngine(1F);
            }
        }
        return success;
    }

    public static TargetHelper.TargetingMode selectionTargetingMode(Spell spell) {
        switch (spell.release.target.type) {
            case AREA, BEAM -> {
                return TargetHelper.TargetingMode.AREA;
            }
            case CURSOR, PROJECTILE, METEOR, SELF -> {
                return TargetHelper.TargetingMode.DIRECT;
            }
        }
        assert true;
        return null;
    }


    public static TargetHelper.TargetingMode impactTargetingMode(Spell spell) {
        switch (spell.release.target.type) {
            case AREA, BEAM, METEOR -> {
                return TargetHelper.TargetingMode.AREA;
            }
            case CURSOR, PROJECTILE, SELF -> {
                return TargetHelper.TargetingMode.DIRECT;
            }
        }
        assert true;
        return null;
    }

    public static EnumSet<TargetHelper.Intent> intents(Spell spell) {
        var intents = new HashSet<TargetHelper.Intent>();
        for (var impact: spell.impact) {
            intents.add(intent(impact.action));
            //return intent(impact.action);
        }
        return EnumSet.copyOf(intents);
    }

    public static TargetHelper.Intent intent(Spell.Impact.Action action) {
        switch (action.type) {
            case DAMAGE, FIRE -> {
                return TargetHelper.Intent.HARMFUL;
            }
            case HEAL -> {
                return TargetHelper.Intent.HELPFUL;
            }
            case STATUS_EFFECT -> {
                var data = action.status_effect;
                var id = new Identifier(data.effect_id);
                var effect = Registries.STATUS_EFFECT.get(id);
                return effect.isBeneficial() ? TargetHelper.Intent.HELPFUL : TargetHelper.Intent.HARMFUL;
            }
        }
        assert true;
        return null;
    }

    public static boolean underApplyLimit(SpellPower.Result spellPower, LivingEntity target, MagicSchool school, Spell.Impact.Action.StatusEffect.ApplyLimit limit) {
        if (limit == null) {
            return true;
        }
        var power = (float) spellPower.nonCriticalValue();
        float cap = limit.health_base + (power * limit.spell_power_multiplier);
        return cap >= target.getMaxHealth();
    }

    // DAMAGE/HEAL OUTPUT ESTIMATION

    public static EstimatedOutput estimate(Spell spell, PlayerEntity caster, ItemStack itemStack) {
        var school = spell.school;
        var damageEffects = new ArrayList<EstimatedValue>();
        var healEffects = new ArrayList<EstimatedValue>();

        boolean forSpellBook = itemStack.getItem() instanceof SpellBookItem;
        var replaceAttributes = (caster.getMainHandStack() != itemStack && !forSpellBook);
        var heldAttributes = caster.getMainHandStack().getAttributeModifiers(EquipmentSlot.MAINHAND);
        var itemAttributes = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (replaceAttributes) {
            caster.getAttributes().removeModifiers(heldAttributes);
            caster.getAttributes().addTemporaryModifiers(itemAttributes);
        }

        for (var impact: spell.impact) {
            var power = SpellPower.getSpellPower(school, caster, forSpellBook ? null : itemStack);
            if (power.baseValue() < impact.action.min_power) {
                power = new SpellPower.Result(power.school(), impact.action.min_power, power.criticalChance(), power.criticalDamage());
            }
            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var damage = new EstimatedValue(power.nonCriticalValue(), power.forcedCriticalValue())
                            .multiply(damageData.spell_power_coefficient);
                    damageEffects.add(damage);
                }
                case HEAL -> {
                    var healData = impact.action.heal;
                    var healing = new EstimatedValue(power.nonCriticalValue(), power.forcedCriticalValue())
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
