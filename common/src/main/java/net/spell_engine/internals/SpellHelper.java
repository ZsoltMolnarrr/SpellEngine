package net.spell_engine.internals;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.InstantCast;
import net.spell_engine.api.effect.StatusEffectClassification;
import net.spell_engine.api.entity.LivingEntityImmunity;
import net.spell_engine.api.spell.weakness.SpellSchoolWeakness;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.tags.SpellEngineEntityTags;
import net.spell_engine.api.entity.SpellEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.compat.CriticalStrikeCompat;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.entity.DamageSourceExtension;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.arrow.ArrowHelper;
import net.spell_engine.internals.casting.SpellBatcher;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCastSyncHelper;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.melee.Melee;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.*;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SpellHelper {
    public static SpellCast.Attempt attemptCasting(PlayerEntity player, ItemStack itemStack, Identifier spellId) {
        return attemptCasting(player, itemStack, spellId, true);
    }

    public static SpellCast.Attempt attemptCasting(PlayerEntity player, ItemStack itemStack, Identifier spellId, boolean checkAmmo) {
        var caster = (SpellCasterEntity)player;
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return SpellCast.Attempt.none();
        }
        var spell = spellEntry.value();
        if (SpellEvents.CASTING_ATTEMPT.PRE.isListened()) {
            var args = new SpellEvents.CastingAttemptEvent.Args(player, spellEntry, itemStack);
            var injected = SpellEvents.CASTING_ATTEMPT.PRE.invokeWithResult(listener -> listener.onCastingAttempt(args));
            if (injected != null) {
                return injected;
            }
        }
        if (caster.getCooldownManager().isCoolingDown(spellEntry)) {
            return SpellCast.Attempt.failOnCooldown(new SpellCast.Attempt.OnCooldownInfo());
        }
        if (checkAmmo) {
            var ammoResult = Ammo.ammoForSpell(player, spell, itemStack);
            if (!ammoResult.satisfied()) {
                return SpellCast.Attempt.failMissingItem(new SpellCast.Attempt.MissingItemInfo(ammoResult.item()));
            }
        }
        if (SpellEvents.CASTING_ATTEMPT.POST.isListened()) {
            var args = new SpellEvents.CastingAttemptEvent.Args(player, spellEntry, itemStack);
            var injected = SpellEvents.CASTING_ATTEMPT.POST.invokeWithResult(listener -> listener.onCastingAttempt(args));
            if (injected != null) {
                return injected;
            }
        }
        return SpellCast.Attempt.success();
    }

    public static float hasteAffectedValue(float value, float haste) {
        return value / haste;
    }

    public static float hasteAffectedValue(LivingEntity caster, SpellSchool school, float value) {
        return hasteAffectedValue(caster, school, value, null);
    }

    public static float hasteAffectedValue(LivingEntity caster, SpellSchool school, float value, ItemStack provisionedWeapon) {
        var haste = SpellPower.getHaste(caster, school); // FIXME: ? Provisioned weapon
        return hasteAffectedValue(value, haste) ;
    }

    public static float getRange(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        var range = spell.range;
        if (spell.range_mechanic != null) {
            switch (spell.range_mechanic) {
                case MELEE -> {
                    double meleeRange = 3;
                    if (caster instanceof PlayerEntity player) {
                        meleeRange = player.getEntityInteractionRange();
                    }
                    range = (float) (meleeRange + spell.range);
                }
            }
        }
        if (caster instanceof PlayerEntity player) {
            for (var modifier: SpellModifiers.of(player, spellEntry)) {
                if (modifier.range_add != 0) {
                    range += modifier.range_add;
                }
            }
        }
        return range;
    }

    public static float getCastDuration(LivingEntity caster, Spell spell) {
        return getCastDuration(caster, spell, null);
    }

    public static float getCastDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        if (spell.active != null && spell.active.cast == null) {
            return 0;
        }
        return hasteAffectedValue(caster, spell.school, spell.active.cast.duration, provisionedWeapon);
    }

    public static SpellCast.Duration getCastTimeDetails(LivingEntity caster, Spell spell) {
        if (spell.active == null) { return SpellCast.Duration.EMPTY; }
        var haste = spell.active.cast.haste_affected
                ? (float) SpellPower.getHaste(caster, spell.school)
                : 1F;
        var duration = hasteAffectedValue(spell.active.cast.duration, haste);
        return new SpellCast.Duration(haste, Math.round(duration * 20F));
    }

    public static int channelTicks(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        var ticks = spellEntry.value().active.cast.channel_ticks;
        if (caster instanceof PlayerEntity player) {
            var modifiers = SpellModifiers.of(player, spellEntry);
            for (var modifier: modifiers) {
                ticks += modifier.channel_ticks_add;
            }
        }
        return ticks;
    }

    public static float getCooldownDuration(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        return getCooldownDuration(caster, spellEntry, null);
    }

    public static boolean isInstantCast(RegistryEntry<Spell> spellEntry, LivingEntity caster) {
        var spell = spellEntry.value();
        if (spell.active == null) { return true; }
        return spell.active.cast.duration == 0
                || (!isChanneled(spell) && InstantCast.instantify(spellEntry, caster));
    }

    public static float getCooldownDuration(LivingEntity caster, RegistryEntry<Spell> spellEntry, ItemStack provisionedWeapon) {
        var spell = spellEntry.value();
        var duration = spell.cost.cooldown.duration;
        if (caster instanceof PlayerEntity player) {
            duration -= SpellModifiers.cooldownDeduction(player, spellEntry);
        }
        if (duration > 0) {
            if (SpellEngineMod.config.haste_affects_cooldown && spell.cost.cooldown.haste_affected) {
                duration = hasteAffectedValue(caster, spell.school, duration, provisionedWeapon);
            }
        }
        return Math.max(duration, 0);
    }

    public static boolean isChanneled(Spell spell) {
        return channelValueMultiplier(spell) != 0;
    }

    public static boolean isInstant(Spell spell) {
        if (spell.active == null) { return true; }
        return spell.active.cast.duration == 0;
    }

    public static float channelValueMultiplier(Spell spell) {
        if (spell.active == null) { return 0F; }
        var ticks = spell.active.cast.channel_ticks;
        if (ticks <= 0) {
            return 0;
        }
        var interval = (spell.active.cast.duration * 20F) / (float)ticks;
        return interval / 20F;
    }

    public static void startCasting(PlayerEntity player, Identifier spellId, float speed, int length) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var spell = spellEntry.value();
        if (spell.active == null) {
            return;
        }
        var itemStack = player.getMainHandStack();
        var attempt = attemptCasting(player, itemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        // Allow clients to specify their haste without validation
        // var details = SpellHelper.getCastTimeDetails(player, spell);
        var process = new SpellCast.Process(player, spellEntry, itemStack.getItem(), speed, length, player.getWorld().getTime());
        SpellCastSyncHelper.setCasting(player, process);
        SoundHelper.playSound(player.getWorld(), player, spell.active.cast.start_sound);
    }

    public static void performSpell(World world, PlayerEntity player, RegistryEntry<Spell> spellEntry, SpellTarget.SearchResult targetResult, SpellCast.Action action, float progress) {
        if (player.isSpectator()) { return; }
        var spell = spellEntry.value();
        var spellId = spellEntry.getKey().get().getValue();

        var heldItemStack = player.getMainHandStack();
        var spellSource = SpellContainerSource.getFirstSourceOfSpell(spellId, player);
        if (spellSource == null) {
            return;
        }
        var attempt = attemptCasting(player, heldItemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        var caster = (SpellCasterEntity)player;
        var targets = targetResult.entities();
        var castingSpeed = caster.getCurrentCastingSpeed();
        // Normalized progress in 0 to 1
        progress = Math.max(Math.min(progress, 1F), 0F);
        var channelMultiplier = 1F;
        var channelTickIndex = 0;
        int incrementChannelTicks = 0;
        boolean shouldPerformImpact = true;
        Supplier<Collection<ServerPlayerEntity>> trackingPlayers = Suppliers.memoize(() -> { // Suppliers.memoize = Lazy
            return PlayerLookup.tracking(player);
        });
        switch (action) {
            case CHANNEL -> {
                channelTickIndex = caster.getChannelTickIndex();
                incrementChannelTicks = 1;
                channelMultiplier = channelValueMultiplier(spell);
                // Compensating with extra damage, for spell with less than intended ticks
                // (due to tick interval shorter than 1 tick.)
                if (caster.getSpellCastProcess() != null) {
                    var channelInterval = caster.getSpellCastProcess().channelInterval(player);
                    if (channelInterval < 1) {
                        channelMultiplier *= (1F / channelInterval);
                    }
                }
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
            case TRIGGER -> {
                // Nothing to do, defaults are okay
            }
        }
        var ammoResult = Ammo.ammoForSpell(player, spell, heldItemStack);

        if (channelMultiplier > 0 && ammoResult.satisfied()) {
            var targeting = spell.target;
            boolean finished = action == SpellCast.Action.RELEASE
                    || (action == SpellCast.Action.TRIGGER && spell.type == Spell.Type.PASSIVE); // For stashed spells release has been done already
            boolean success = true;
            if (targeting.cap > 0) {
                targets = targets.stream()
                        .sorted(Comparator.comparingDouble(target -> target.squaredDistanceTo(player.getPos())))
                        .limit(targeting.cap)
                        .toList();
            }

            Consumer<DeliveryCompletion> completion = null;
            if (finished) {
                float finalProgress = progress;
                List<Entity> finalTargets = targets;
                completion = (completionArgs) -> {
                    var deliverySuccess = completionArgs.success();
                    if (deliverySuccess) {
                        sendReleaseFx(world, player, spellEntry);
                        AnimationHelper.sendAnimation(player, trackingPlayers.get(), SpellCast.Animation.RELEASE, spell.release.animation, castingSpeed);

                        consumeSpellCost(player, finalProgress, spellSource, spellId, spellEntry, heldItemStack, ammoResult, false);

                        var args = new SpellEvents.SpellCastEvent.Args(player, spellEntry, finalTargets, action, finalProgress);
                        SpellEvents.SPELL_CAST.invoke((listener) -> listener.onSpellCast(args));
                    }
                };
            }

            if (shouldPerformImpact) {
                consumeAttemptCost(player, spellEntry);
                // Channel tick or charge release
                success = false;
                var context = new ImpactContext(channelMultiplier,
                        1F,
                        null,
                        SpellPower.getSpellPower(spell.school, player),
                        focusMode(spell),
                        channelTickIndex);
                success = resolveAndDeliver(world, player, spellEntry, targetResult, context, completion);
                caster.setChannelTickIndex(channelTickIndex + incrementChannelTicks);
            } else {
                if (finished && completion != null) {
                    // Channel release
                    completion.accept(new DeliveryCompletion(true));
                }
            }
//            if (finished && success) {
//                ParticleHelper.sendBatches(player, spell.release.particles);
//                SoundHelper.playSound(world, player, spell.release.sound);
//                AnimationHelper.sendAnimation(player, trackingPlayers.get(), SpellCast.Animation.RELEASE, spell.release.animation, castingSpeed);
//
//                consumeSpellCost(player, progress, spellSource, spellId, spell, heldItemStack, ammoResult, false);
//
//                var args = new SpellEvents.SpellCastEvent.Args(player, spellEntry, targets, action, progress);
//                SpellEvents.SPELL_CAST.invoke((listener) -> listener.onSpellCast(args));
//            }
        }
    }


    /**
     * Routes a resolved {@link SpellTarget.SearchResult} through the delivery system based on the
     * spell's targeting type.  Called from both {@link #performSpell} (player, client-supplied
     * targets) and {@link #targetAndPerformSpell} (entity, server-resolved targets).
     */
    private static boolean resolveAndDeliver(
            World world,
            LivingEntity caster,
            RegistryEntry<Spell> spellEntry,
            SpellTarget.SearchResult targetResult,
            ImpactContext context,
            @Nullable Consumer<DeliveryCompletion> completion) {
        var spell = spellEntry.value();
        var targeting = spell.target;
        var targets = targetResult.entities();
        boolean success = false;
        switch (targeting.type) {
            case NONE -> {
                success = deliver(world, spellEntry, caster, List.of(), context, null, completion);
            }
            case CASTER -> {
                var targetsWithContext = List.of(new DeliveryTarget(caster, context));
                success = deliver(world, spellEntry, caster, targetsWithContext, context, null, completion);
            }
            case AIM -> {
                var aim = targeting.aim;
                var firstTarget = targets.stream().findFirst();
                List<DeliveryTarget> targetsWithContext = List.of();
                if (firstTarget.isPresent()) {
                    var target = firstTarget.get();
                    targetsWithContext = List.of(new DeliveryTarget(target, context));
                }
                if (!aim.required || firstTarget.isPresent()) {
                    var location = targetResult.location();
                    if (location != null && firstTarget.isEmpty() && aim.reposition_vertically != 0) {
                        var collidedLocation = TargetHelper.findSolidBelow(caster, location, world, aim.reposition_vertically);
                        if (collidedLocation != null) {
                            location = collidedLocation;
                        }
                    }
                    success = deliver(world, spellEntry, caster, targetsWithContext, context, location, completion);
                }
                // Very specific attempt failure display, generic solution would be very difficult
                if (!success && aim.required && firstTarget.isEmpty()) {
                    if (caster instanceof ServerPlayerEntity serverPlayer) {
                        ServerPlayNetworking.send(serverPlayer, new Packets.SpellMessage("hud.cast_attempt_error.missing_target", Formatting.RED));
                    }
                }
            }
            case AREA -> {
                var center = caster.getPos().add(0, caster.getHeight() / 2F, 0);
                var area = spell.target.area;
                var range = getRange(caster, spellEntry) * caster.getScale();
                final var centeredContext = context; // .position(center);
                double squaredRange = range * range;
                var targetsWithContext = targets.stream().map(target -> {
                    float distanceBasedMultiplier = 1F;
                    switch (area.distance_dropoff) {
                        case NONE -> { }
                        case SQUARED -> {
                            distanceBasedMultiplier = (float) ((squaredRange - target.squaredDistanceTo(center)) / squaredRange);
                            distanceBasedMultiplier = Math.max(distanceBasedMultiplier, 0F);
                        }
                    }
                    return new DeliveryTarget(target, centeredContext.distance(distanceBasedMultiplier));
                }).toList();
                // `forceSuccess` is true because area spells should always go to cooldown
                deliver(world, spellEntry, caster, targetsWithContext, context, null, completion, true, false);
                // success = true; // Always true, otherwise area spells don't go to CD without targets
            }
            case BEAM -> {
                var targetsWithContext = targets.stream().map(target -> new DeliveryTarget(target, context)).toList();
                success = deliver(world, spellEntry, caster, targetsWithContext, context, null, completion);
            }
            case FROM_TRIGGER -> {
                var targetsWithContext = targets.stream().map(target -> new DeliveryTarget(target, context)).toList();
                success = deliver(world, spellEntry, caster, targetsWithContext, context, targetResult.location(), completion);
            }
            default -> throw new IllegalStateException("Unexpected value: " + targeting.type);
        }
        return success;
    }

    /**
     * Server-side entry point for non-player entities (e.g. summoned companions) to cast a spell.
     * Performs targeting based on the entity's current rotation/position, then runs the full
     * delivery pipeline.  No player-side costs (ammo, exhaust, cooldown, animations) are applied;
     * callers are responsible for managing their own cooldown.
     *
     * <p>Must be called on the server; silently no-ops on the client.</p>
     */
    public static void targetAndPerformSpell(World world, LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        if (world.isClient()) return;
        var spell = spellEntry.value();
        if (spell.active == null) return;

        var context = new ImpactContext()
                .power(SpellPower.getSpellPower(spell.school, caster))
                .target(focusMode(spell));

        var targetResult = SpellTarget.findTargets(caster, spellEntry, SpellTarget.SearchResult.empty(), true);

        // Apply target cap, sorted by distance to caster
        var targets = targetResult.entities();
        if (spell.target.cap > 0) {
            targets = targets.stream()
                    .sorted(Comparator.comparingDouble(t -> t.squaredDistanceTo(caster.getPos())))
                    .limit(spell.target.cap)
                    .toList();
            targetResult = new SpellTarget.SearchResult(targets, targetResult.location());
        }

        resolveAndDeliver(world, caster, spellEntry, targetResult, context,
                (completionArgs) -> {
                    if (completionArgs.success()) {
                        sendReleaseFx(world, caster, spellEntry);
                    }
                });
    }

    private static void sendReleaseFx(World world, LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        ParticleHelper.sendBatches(caster, spell.release.particles);
        if (spell.release.particles_scaled_with_ranged != null) {
            ParticleBatch[] scaledParticles = new ParticleBatch[spell.release.particles_scaled_with_ranged.length];
            for (int i = 0; i < spell.release.particles_scaled_with_ranged.length; i++) {
                var particles = spell.release.particles_scaled_with_ranged[i];
                var range = getRange(caster, spellEntry);
                scaledParticles[i] = particles.copy().scale(range);
            }
            ParticleHelper.sendBatches(caster, scaledParticles);
        }
        SoundHelper.playSound(world, caster, spell.release.sound);
    }

    private static void consumeAttemptCost(PlayerEntity player, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        if (spell.cost.cooldown != null) {
            var attemptCooldown = spell.cost.cooldown.attempt_duration;
            if (attemptCooldown > 0) {
                var durationTicks = Math.round(attemptCooldown * 20F);
                ((SpellCasterEntity) player).getCooldownManager().set(spellEntry, durationTicks);
            }
        }
    }

    private static void consumeSpellCost(PlayerEntity player, float progress, SpellContainerSource.SourcedContainer spellSource, Identifier spellId, RegistryEntry<Spell> spellEntry, ItemStack heldItemStack, Ammo.Result ammoResult, boolean scheduled) {
        var spell = spellEntry.value();
        var batching = spell.cost.batching;
        if (batching && !scheduled) {
            if (((SpellBatcher)player).hasBatchedCost(spellId)) {
                return;
            }
            ((WorldScheduler)player.getWorld()).schedule(0, () -> consumeSpellCost(player, progress, spellSource, spellId, spellEntry, heldItemStack, ammoResult, true));
            ((SpellBatcher)player).batchCost(spellId, true);
            return;
        }

        // Consume things
        // Cooldown
        imposeCooldown(player, spellSource, spellEntry, progress);
        // Exhaust
        player.addExhaustion(spell.cost.exhaust * SpellEngineMod.config.spell_cost_exhaust_multiplier);
        // Durability
        if (SpellEngineMod.config.spell_cost_durability_allowed && spell.cost.durability > 0) {
            var stackToDamage = (spellSource.itemStack() != null && spellSource.itemStack().isDamageable()) ? spellSource.itemStack() : heldItemStack;
            stackToDamage.damage(spell.cost.durability, player, EquipmentSlot.MAINHAND);
        }
        // Item
        Ammo.consume(ammoResult, player);
        // Status effect
        if (spell.cost.effect_id != null) {
            var effect = Registries.STATUS_EFFECT.getEntry(Identifier.of(spell.cost.effect_id));
            if (effect.isPresent()) {
                player.removeStatusEffect(effect.get());
            }
        }
        if (SpellEvents.COST_CONSUME.isListened()) {
            var args = new SpellEvents.SpellCostConsumeEvent.Args(player, spellEntry, heldItemStack);
            SpellEvents.COST_CONSUME.invoke(l -> l.onSpellCostConsume(args));
        }
    }

    public record DeliveryTarget(Entity entity, ImpactContext context) {}
    public record DeliveryCompletion(boolean success) {}
    public static boolean deliver(World world, RegistryEntry<Spell> spellEntry, LivingEntity caster, List<DeliveryTarget> targets, ImpactContext context, @Nullable Vec3d targetLocation, Consumer<DeliveryCompletion> completion) {
        return deliver(world, spellEntry, caster, targets, context, targetLocation, completion, false, false);
    }
    public static boolean deliver(World world, RegistryEntry<Spell> spellEntry, LivingEntity caster, List<DeliveryTarget> targets, ImpactContext context,
                                  @Nullable Vec3d targetLocation, @Nullable Consumer<DeliveryCompletion> completion, boolean forceSuccess, boolean scheduled) {
        var spell = spellEntry.value();

        if (spell.deliver.delay > 0) {
            if (scheduled) {
                Predicate<Entity> validator = (entity) -> !(entity == null || entity.isRemoved());
                if (!validator.test(caster)) {
                    return false;
                }
                targets = targets.stream().filter(target -> validator.test(target.entity)).toList();
            } else {
                List<DeliveryTarget> finalTargets = targets;
                ((WorldScheduler) world).schedule(spell.deliver.delay, () -> deliver(world, spellEntry, caster, finalTargets, context, targetLocation, completion, forceSuccess, true));
                return true;
            }
        }

        var delivered = false;
        switch (spell.deliver.type) {
            case DIRECT -> {
                var anySuccess = false;
                var casterPos = caster.getPos().add(0, caster.getHeight() / 2F, 0);
                if (targets.isEmpty() && targetLocation != null
                        && spell.area_impact != null) { // Special check to allow area impacts only, in the absence of targets
                    var position = targetLocation.lerp(casterPos, 0.001F);
                    var targetSpecificContext = context.position(position);
                    performImpacts(world, caster, caster, null, spellEntry, spell.impacts, targetSpecificContext);
                    anySuccess = true; // The area impact will be executed, hence always true
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity;
                        var position = target == caster
                                ? casterPos
                                : target.getPos().add(0, target.getHeight() / 2F, 0).lerp(casterPos, 0.01F);
                        var targetSpecificContext = targeted.context.position(position);
                        var result = performImpacts(world, caster, target, target, spellEntry, spell.impacts, targetSpecificContext);
                        anySuccess = anySuccess || result;
                    }
                }
                delivered = anySuccess;
            }
            case PROJECTILE -> {
                if (targets.isEmpty()) {
                    shootProjectile(world, caster, null, spellEntry, context);
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity;
                        var targetSpecificContext = targeted.context;
                        shootProjectile(world, caster, target, spellEntry, targetSpecificContext);
                    }
                }
                delivered = true;
            }
            case METEOR -> {
                var anyLaunched = false;
                if (targets.isEmpty() && targetLocation != null) {
                    fallProjectile(world, caster, null, targetLocation, spellEntry, context);
                    anyLaunched = true;
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity;
                        var targetSpecificContext = targeted.context;
                        fallProjectile(world, caster, target, null, spellEntry, targetSpecificContext);
                        anyLaunched = true;
                    }
                }
                delivered = anyLaunched;
            }
            case CLOUD -> {
                var placedAny = false;
                if (targets.isEmpty() && targetLocation != null) {
                    placeCloud(world, caster, null, targetLocation, spellEntry, context.position(targetLocation));
                    placedAny = true;
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity;
                        var targetSpecificContext = targeted.context;
                        placeCloud(world, caster, target, null, spellEntry, targetSpecificContext);
                        placedAny = true;
                    }
                }
                delivered = placedAny;
            }
            case SHOOT_ARROW -> {
                ArrowHelper.shootArrow(world, caster, spellEntry, context);
                delivered = true;
            }
            case AFFECT_ARROW -> {
                if (caster instanceof SpellCasterEntity shooter) {
                    var arrowContext = shooter.getArrowShootContext();
                    arrowContext.activeSpells.add(spellEntry);
                }
                delivered = true;
            }
            case MELEE -> {
                if (spell.deliver.melee != null
                        && !spell.deliver.melee.attacks.isEmpty()) {
                    var attackers = !targets.isEmpty()
                            ? targets.stream().map(e -> e.entity).toList()
                            : List.of(caster);
                    var meleeData = spell.deliver.melee;
                    var spellId = spellEntry.getKey().get().getValue();
                    var attacks = meleeData.attacks;
                    if (context.isChanneled()) {
                        var index = context.channelTickIndex() % attacks.size();
                        attacks = List.of(attacks.get(index));
                    }
                    for (var attacker: attackers) {
                        if (!attacker.isOnGround() && !spell.deliver.melee.allow_airborne) {
                            break;
                        }
                        if (attacker instanceof ServerPlayerEntity serverPlayer) {
                            // Map to resolved MeleeAttack structures
                            var meleeAttacks = Melee.createMeleeAttacks(serverPlayer, attacks, spellEntry);
                            // Send AttackAvailable packet to client
                            var packet = new Packets.AttackAvailable(spellId, meleeAttacks);
                            ServerPlayNetworking.send(serverPlayer, packet);
                            delivered = true;
                        }
                    }
                }
            }
            case STASH_EFFECT -> {
                var anyAdded = false;
                var stash = spell.deliver.stash_effect;
                var id = Identifier.of(stash.id);
                var effect = Registries.STATUS_EFFECT.getEntry(id).get();

                var amplifier = stash.amplifier;
                if (stash.amplifier_power_multiplier != 0) {
                    var power = SpellPower.getSpellPower(spell.school, caster);
                    amplifier += (int)(stash.amplifier_power_multiplier * power.nonCriticalValue());
                }
                if (caster instanceof PlayerEntity player) {
                    var spellModifiers = SpellModifiers.of(player, spellEntry);
                    for (var modifier: spellModifiers) {
                        amplifier += modifier.stash_amplifier_add;
                    }
                }
                for (var targeted: targets) {
                    if (targeted.entity() instanceof LivingEntity livingEntity) {
                        if (stash.stacking) {
                            var stack = -1;
                            var existingInstance = livingEntity.getStatusEffect(effect);
                            if (existingInstance != null) {
                                stack = existingInstance.getAmplifier();
                                livingEntity.removeStatusEffect(effect);
                            }
                            stack += 1;
                            var instance = new StatusEffectInstance(effect, (int) (stash.duration * 20), Math.min(stack, amplifier), false, stash.show_particles, true);
                            livingEntity.addStatusEffect(instance);
                        } else {
                            var instance = new StatusEffectInstance(effect, (int) (stash.duration * 20), amplifier, false, stash.show_particles, true);
                            livingEntity.addStatusEffect(instance);
                        }
                        anyAdded = true;
                    }
                }
                delivered = anyAdded;
            }
            case CUSTOM -> {
                if (spell.deliver.custom != null) {
                    var handler = SpellHandlers.customDelivery.get(spell.deliver.custom.handler);
                    if (handler != null) {
                        delivered = handler.onSpellDelivery(world, spellEntry, caster, targets, context, targetLocation);
                    }
                }
            }
        }

        if (completion != null) {
            completion.accept(new DeliveryCompletion(delivered || forceSuccess));
        }

        return delivered;
    }

    public static void imposeCooldown(PlayerEntity player, SpellContainerSource.SourcedContainer source, RegistryEntry<Spell> spellEntry, float progress) {
        var spell = spellEntry.value();
        var duration = cooldownToSet(player, spellEntry, progress);
        var durationTicks = Math.round(duration * 20F);
        if (duration > 0) {
            ((SpellCasterEntity) player).getCooldownManager().set(spellEntry, durationTicks);
        }
        if (SpellEngineMod.config.spell_item_cooldown_lock && spell.cost.cooldown.hosting_item && source.itemStack() != null) {
            var hostingItem = source.itemStack().getItem();
            var itemCooldowns = player.getItemCooldownManager();
            if (source.itemStack().isIn(SpellEngineItemTags.SPELL_BOOK)) {
                durationTicks += (int) (SpellEngineMod.config.spell_book_additional_cooldown * 20F);
            }
            var durationLeft = ((ItemCooldownManagerExtension)itemCooldowns).SE_getLastCooldownDuration(hostingItem)
                    * itemCooldowns.getCooldownProgress(hostingItem, 0);
            if (durationTicks > durationLeft) {
                itemCooldowns.set(hostingItem, durationTicks);
            }
        }
    }

    private static float cooldownToSet(LivingEntity caster, RegistryEntry<Spell> spellEntry, float progress) {
        var spell = spellEntry.value();
        if (spell.cost.cooldown.proportional) {
            return getCooldownDuration(caster, spellEntry) * progress;
        } else {
            return getCooldownDuration(caster, spellEntry);
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

    public static void shootProjectile(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        shootProjectile(world, caster, target, spellEntry, context, 0);
    }

    public static void shootProjectile(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context, int sequenceIndex) {
        if (world.isClient) {
            return;
        }

        var spell = spellEntry.value();
        var launchPoint = launchPoint(caster);
        var data = spell.deliver.projectile;
        var projectileData = data.projectile;
        var mutablePerks = projectileData.perks.copy();
        var mutableLaunchProperties = data.launch_properties.copy();

        if (caster instanceof PlayerEntity player) {
            var spellModifiers = SpellModifiers.of(player, spellEntry);
            for (var modifier: spellModifiers) {
                if (modifier.projectile_launch != null) {
                    mutableLaunchProperties.mutatingCombine(modifier.projectile_launch);
                }
                if (modifier.projectile_perks != null) {
                    mutablePerks.mutatingCombine(modifier.projectile_perks);
                }
            }
        }

        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FLY, spellEntry, context, mutablePerks);


        if (SpellEvents.PROJECTILE_SHOOT.isListened()) {
            SpellEvents.PROJECTILE_SHOOT.invoke((listener) -> listener.onProjectileLaunch(
                    new SpellEvents.ProjectileLaunchEvent(projectile, mutableLaunchProperties, caster, target, spellEntry, context, sequenceIndex)));
        }
        var velocity = mutableLaunchProperties.velocity;
        var divergence = projectileData.divergence;
        var directionPitch = data.inherit_shooter_pitch ? caster.getPitch() : 0;
        var directionYaw = data.inherit_shooter_yaw ? caster.getYaw() : 0;
        if (data.direct_towards_target && target != null) {
            var directionVector = target.getPos().subtract(caster.getPos()).normalize();
            // Yaw and pitch from distance vector
            directionPitch = (float) VectorHelper.pitchFromNormalized(directionVector);
            directionYaw = (float) VectorHelper.yawFromNormalized(directionVector);
        }
        if (data.inherit_shooter_velocity) {
            projectile.setVelocity(caster, directionPitch, directionYaw, 0, velocity, divergence);
        } else {
            if (data.direction_offsets != null && data.direction_offsets.length > 0
                && (!data.direction_offsets_require_target || target != null)) {
                var baseIndex = context.isChanneled() ? context.channelTickIndex() : sequenceIndex;
                var index = baseIndex % data.direction_offsets.length;
                var offset = data.direction_offsets[index];
                directionPitch += offset.pitch;
                directionYaw += offset.yaw;
            }
            // var look = caster.getRotationVector().normalize();
            var look = caster.getRotationVector(directionPitch, directionYaw).normalize();
            projectile.setVelocity(look.x, look.y, look.z, velocity, divergence);
        }
        projectile.range = spell.range;
        projectile.setPitch(directionPitch);
        projectile.setYaw(directionYaw);

        projectile.setFollowedTarget(target);
        world.spawnEntity(projectile);
        SoundHelper.playSound(world, projectile, mutableLaunchProperties.sound);

        var allowExtraShoot = (context.isChanneled() && mutableLaunchProperties.extra_launch_mod >= 0)
                ? context.channelTickIndex() % mutableLaunchProperties.extra_launch_mod == 0
                : true;
        if (sequenceIndex == 0 && mutableLaunchProperties.extra_launch_count > 0 && allowExtraShoot) {
            for (int i = 0; i < mutableLaunchProperties.extra_launch_count; i++) {
                var ticks = (i + 1) * mutableLaunchProperties.extra_launch_delay;
                var nextSequenceIndex = i + 1;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    shootProjectile(world, caster, target, spellEntry, context, nextSequenceIndex);
                });
            }
        }
    }

    public static boolean fallProjectile(World world, LivingEntity caster, Entity target, @Nullable Vec3d targetLocation, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        return fallProjectile(world, caster, target, targetLocation, spellEntry, context, 0);
    }

    public static boolean fallProjectile(World world, LivingEntity caster, Entity target, @Nullable Vec3d targetLocation, RegistryEntry<Spell> spellEntry, ImpactContext context, int sequenceIndex) {
        if (world.isClient) {
            return false;
        }

        Vec3d targetPosition = (target != null) ? target.getPos() : targetLocation;
        if (targetPosition == null) {
            return false;
        }

        var spell = spellEntry.value();
        var meteor = spell.deliver.meteor;
        var height = meteor.launch_height;
        var launchPoint = targetPosition.add(0, height, 0);
        var data = spell.deliver.meteor;
        var projectileData = data.projectile;
        var mutableLaunchProperties = data.launch_properties.copy();
        var mutablePerks = projectileData.perks.copy();

        if (caster instanceof PlayerEntity player) {
            var spellModifiers = SpellModifiers.of(player, spellEntry);
            for (var modifier: spellModifiers) {
                if (modifier.projectile_launch != null) {
                    mutableLaunchProperties.mutatingCombine(modifier.projectile_launch);
                }
                if (modifier.projectile_perks != null) {
                    mutablePerks.mutatingCombine(modifier.projectile_perks);
                }
            }
        }

        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FALL, spellEntry, context, mutablePerks);

        if (SpellEvents.PROJECTILE_FALL.isListened()) {
            SpellEvents.PROJECTILE_FALL.invoke((listener) -> listener.onProjectileLaunch(new SpellEvents.ProjectileLaunchEvent(projectile, mutableLaunchProperties, caster, target, spellEntry, context, sequenceIndex)));
        }

        projectile.setYaw(0);
        projectile.setPitch(90);

        if (launchSequenceEligible(sequenceIndex, meteor.divergence_requires_sequence)) {
            projectile.setVelocity( 0, - 1, 0, mutableLaunchProperties.velocity, 0.5F, projectileData.divergence);
        } else {
            projectile.setVelocity(new Vec3d(0, - mutableLaunchProperties.velocity, 0));
        }
        if (launchSequenceEligible(sequenceIndex, meteor.follow_target_requires_sequence)) {
            projectile.setFollowedTarget(target);
        } else {
            projectile.setFollowedTarget(null);
        }
        if (meteor.launch_radius > 0 && launchSequenceEligible(sequenceIndex, meteor.offset_requires_sequence)) {
            var randomAngle = Math.toRadians(world.random.nextFloat() * 360);
            var offset = (new Vec3d(meteor.launch_radius, 0, 0)).rotateY((float) randomAngle);
            projectile.setPosition(projectile.getPos().add(offset));
        }

        projectile.prevYaw = projectile.getYaw();
        projectile.prevPitch = projectile.getPitch();
        projectile.range = height;

        world.spawnEntity(projectile);

        if (sequenceIndex == 0 && mutableLaunchProperties.extra_launch_count > 0) {
            for (int i = 0; i < mutableLaunchProperties.extra_launch_count; i++) {
                var ticks = (i + 1) * mutableLaunchProperties.extra_launch_delay;
                var nextSequenceIndex = i + 1;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    fallProjectile(world, caster, target, targetLocation, spellEntry, context, nextSequenceIndex);
                });
            }
        }
        return true;
    }

    private static boolean launchSequenceEligible(int index, int rule) {
        if (rule == 0) {
            return false;
        }
        if (rule > 0) {
            return index >= rule;
        } else {
            return index < (-1 * rule);
        }
    }

    private static void directImpact(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        performImpacts(world, caster, target, target, spellEntry, spellEntry.value().impacts, context);
    }

    private static void beamImpact(World world, LivingEntity caster, List<Entity> targets, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        for(var target: targets) {
            performImpacts(world, caster, target, target, spellEntry, spellEntry.value().impacts, context.position(target.getPos()));
        }
    }
    public static void fallImpact(LivingEntity caster, Entity projectile, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var adjustedCenter = context.position().add(0, 1, 0); // Adding a bit of height to avoid raycast hitting the ground
        performImpacts(projectile.getWorld(), caster, null, projectile, spellEntry, spellEntry.value().impacts, context.position(adjustedCenter));
    }
    public static boolean projectileImpact(LivingEntity caster, Entity projectile, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        return performImpacts(projectile.getWorld(), caster, target, projectile, spellEntry, spellEntry.value().impacts, context);
    }

    public static boolean arrowImpact(LivingEntity caster, Entity projectile, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var spell = spellEntry.value();
        if (spell.impacts != null) {
            if (context.power() == null) {
                context = context.power(SpellPower.getSpellPower(spell.school, caster));
            }
            return performImpacts(projectile.getWorld(), caster, target, projectile, spellEntry, spell.impacts, context);
        }
        return false;
    }

    public static boolean meleeImpact(LivingEntity caster, List<Entity> targets, RegistryEntry<Spell> spellEntry, @Nullable ImpactContext context) {
        var spell = spellEntry.value();
        var anySuccess = false;
        if (spell.impacts != null) {
            if (context.power() == null) {
                context = context.power(SpellPower.getSpellPower(spell.school, caster));
            }

            var world = caster.getWorld();
            var casterPos = caster.getPos().add(0, caster.getHeight() / 2F, 0);

            for(var target: targets) {
                var position = target == caster
                        ? casterPos
                        : target.getPos().add(0, target.getHeight() / 2F, 0).lerp(casterPos, 0.01F);
                var targetSpecificContext = context.position(position);
                var result = performImpacts(world, caster, target, target, spellEntry, spell.impacts, targetSpecificContext);
                anySuccess = anySuccess || result;
            }
        }
        return anySuccess;
    }

    public static boolean lookupAndPerformAreaImpact(Spell.AreaImpact area_impact, RegistryEntry<Spell> spellEntry, LivingEntity caster, Entity exclude, @Nullable Entity aoeSource,
                                                  List<Spell.Impact> impacts, ImpactContext context, boolean additionalTargetLookup) {
        var center = context.position();
        var radius = area_impact.combinedRadius(context.power().baseValue());

        var contextEntity = aoeSource != null ? aoeSource : caster;
        var targets = TargetHelper.targetsFromArea(contextEntity.getWorld(), aoeSource, center, contextEntity.getRotationVector(), radius, area_impact.area, null);
        if (exclude != null) {
            targets.remove(exclude);
        }
        var result = applyAreaImpact(contextEntity.getWorld(), caster, targets, radius, area_impact.area, spellEntry, impacts,
                context.target(SpellTarget.FocusMode.AREA), additionalTargetLookup, area_impact.execute_action_type);
        if (aoeSource != null) {
            ParticleHelper.sendBatches(aoeSource, area_impact.particles);
        } else {
            ParticleHelper.sendBatches(center, caster, area_impact.particles);
        }

        SoundHelper.playSound(contextEntity.getWorld(), contextEntity, area_impact.sound);
        return result;
    }

    private static boolean applyAreaImpact(World world, LivingEntity caster, List<Entity> targets,
                                        float range, Spell.Target.Area area,
                                        RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context,
                                        boolean additionalTargetLookup, @Nullable Spell.Impact.Action.Type filteredAction) {
        double squaredRange = range * range;
        var center = context.position();
        var anyPerformed = false;
        for(var target: targets) {
            float distanceBasedMultiplier = 1F;
            switch (area.distance_dropoff) {
                case NONE -> { }
                case SQUARED -> {
                    distanceBasedMultiplier = (float) ((squaredRange - target.squaredDistanceTo(center)) / squaredRange);
                    distanceBasedMultiplier = Math.max(distanceBasedMultiplier, 0F);
                }
            }
            anyPerformed = performImpacts(world, caster, target, target, spellEntry, impacts, context
                            .distance(distanceBasedMultiplier),
                    additionalTargetLookup, filteredAction
            );
        }
        return anyPerformed;
    }

    public record ImpactContext(float channel, float distance, @Nullable Vec3d position, SpellPower.Result power, SpellTarget.FocusMode focusMode, int channelTickIndex) {
        public ImpactContext() {
            this(1, 1, null, null, SpellTarget.FocusMode.DIRECT, 0);
        }

        public ImpactContext channeled(float multiplier) {
            return new ImpactContext(multiplier, distance, position, power, focusMode, channelTickIndex);
        }

        public ImpactContext distance(float multiplier) {
            return new ImpactContext(channel, multiplier, position, power, focusMode, channelTickIndex);
        }

        public ImpactContext position(Vec3d position) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex);
        }

        public ImpactContext power(SpellPower.Result spellPower) {
            return new ImpactContext(channel, distance, position, spellPower, focusMode, channelTickIndex);
        }

        public ImpactContext target(SpellTarget.FocusMode focusMode) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex);
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

    public static boolean performImpacts(World world, LivingEntity caster, @Nullable Entity target, Entity aoeSource, RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context) {
        return performImpacts(world, caster, target, aoeSource, spellEntry, impacts, context, true, null);
    }

    public static boolean performImpacts(World world, LivingEntity caster, @Nullable Entity target, Entity aoeSource,
                                         RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context,
                                         boolean additionalTargetLookup, @Nullable Spell.Impact.Action.Type filteredAction) {
        var trackers = target != null ? PlayerLookup.tracking(target) : null;
        SpellTarget.Intent selectedIntent = null;

        var extendedImpacts = SpellModifiers.extendedImpactsOf(caster, spellEntry);
        var area_impact = extendedImpacts.areaImpact();
        var mutableImpacts = extendedImpacts.impacts();

        var perform = true;
        if (additionalTargetLookup && area_impact != null && area_impact.force_indirect) {
            perform = false;
        }

        EnumSet<Spell.Impact.Action.Type> performedActionTypes = EnumSet.noneOf(Spell.Impact.Action.Type.class);
        if (perform) {
            for (var impact : mutableImpacts) {
                var intent = impactIntent(impact.action);
                if (!impact.action.apply_to_caster // Only filtering for cases when another entity is actually targeted
                        && (selectedIntent != null && selectedIntent != intent)) {
                    // Filter out mixed intents
                    // So dual intent spells either damage or heal, and not do both
                    continue;
                }
                if (filteredAction != null && impact.action.type != filteredAction) {
                    // Filter out actions that are not of the specified type
                    continue;
                }
                if (additionalTargetLookup && !impact.action.allow_on_center_target) {
                    // Skip center target if additional target lookup is enabled
                    continue;
                }

                if (target != null) {
                    var result = performImpact(world, caster, target, spellEntry, impact, context, trackers);
                    if (result) {
                        performedActionTypes.add(impact.action.type);
                        selectedIntent = intent;
                    }
                }
            }
        }

        if (area_impact != null
                && additionalTargetLookup
                && (shouldApplyAreaImpact(area_impact, performedActionTypes) || target == null) ) {
            var exclude = area_impact.force_indirect ? null : target;
            lookupAndPerformAreaImpact(area_impact, spellEntry, caster, exclude, aoeSource, impacts, context, false);
            if (caster instanceof PlayerEntity player) {
                ((WorldScheduler)world).schedule(0, () -> {
                    var location = target != null ? target.getPos() : context.position;
                    SpellTriggers.onSpellAreaImpact(player, target, location, spellEntry);
                });
            }
        }

        var anyPerformed = !performedActionTypes.isEmpty();
        if (anyPerformed && caster instanceof PlayerEntity player) {
            ((WorldScheduler)world).schedule(0, () -> {
                SpellTriggers.onSpellImpactAny(player, target, aoeSource, spellEntry);
            });
        }

        return anyPerformed;
    }

    private static boolean shouldApplyAreaImpact(Spell.AreaImpact areaImpact, EnumSet<Spell.Impact.Action.Type> performedActionTypes) {
        if (areaImpact.triggering_action_type == null)  {
            return true; // No specific action type, always apply
        }
        return performedActionTypes.contains(areaImpact.triggering_action_type);
    }

    private static final float knockbackDefaultStrength = 0.4F;

    private static boolean performImpact(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry,
                                         Spell.Impact impact, ImpactContext context, Collection<ServerPlayerEntity> trackers) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        var critical = false;
        boolean isKnockbackPushed = false;
        var spell = spellEntry.value();
        try {
            // Guards
            if (impact.chance < 1F && world.random.nextFloat() > impact.chance) {
                return false; // Skip impact if chance is not met
            }
            var school = impact.school != null ? impact.school : spell.school;
            var originalTarget = target;

            if (impact.action.apply_to_caster) {
                target = caster;
            } else {
                var intent = impactIntent(impact.action);
                if (!EntityRelations.actionAllowed(context.focusMode(), intent, caster, target)) {
                    return false;
                }
            }
            // Merge school-level weaknesses with spell-level target modifiers
            var mergedTargetModifiers = new ArrayList<>(impact.target_modifiers);
            var schoolWeaknesses = SpellSchoolWeakness.getWeaknesses(school);
            if (!schoolWeaknesses.isEmpty()) {
                for (var schoolWeakness: schoolWeaknesses) {
                    if (schoolWeakness.impact_type() == null || schoolWeakness.impact_type() == impact.action.type) {
                        mergedTargetModifiers.addFirst(schoolWeakness.weakness()); // Prepend school weaknesses
                    }
                }
            }

            var conditionResult = evaluateImpactConditions(target, caster, mergedTargetModifiers);
            if (!conditionResult.allowed) {
                return false;
            }
            var targetWasAlive = true;
            if (target instanceof LivingEntity livingEntity) {
                targetWasAlive = livingEntity.isAlive();
            }

            // Power calculation

            List<Spell.Modifier> spellModifiers = List.of();
            if (caster instanceof PlayerEntity player) {
                spellModifiers = SpellModifiers.ofImpact(player, spellEntry, impact);
            }

            double particleMultiplier = 1 * context.total();
            var power = context.power();
            if (power == null || power.school() != school) {
                power = SpellPower.getSpellPower(school, caster);
            }
            if (impact.attribute != null) {
                var attributeOverride = Registries.ATTRIBUTE.getEntry(Identifier.of(impact.attribute)).get();
                double value;
                if (impact.attribute_from_target
                        && originalTarget instanceof LivingEntity livingEntity) {
                    value = livingEntity.getAttributes().hasAttribute(attributeOverride)
                            ? livingEntity.getAttributeValue(attributeOverride)
                            : 0;
                } else {
                    value = caster.getAttributeValue(attributeOverride);
                }
                power = new SpellPower.Result(power.school(), value, power.criticalChance(), power.criticalDamage());
            }

            var powerModifiers = new ArrayList<>(conditionResult.modifiers());
            for (var spellModifier: spellModifiers) {
                if (spellModifier.power_modifier != null) {
                    powerModifiers.add(spellModifier.power_modifier);
                }
            }
            var bonusPower = 1 + (powerModifiers.stream().map(modifier -> modifier.power_multiplier).reduce(0F, Float::sum));
            var bonusCritChance = powerModifiers.stream().map(modifier -> modifier.critical_chance_bonus).reduce(0F, Float::sum);
            var bonusCritDamage = powerModifiers.stream().map(modifier -> modifier.critical_damage_bonus).reduce(0F, Float::sum);
            power = new SpellPower.Result(power.school(),
                    power.baseValue() * bonusPower,
                    power.criticalChance() + bonusCritChance,
                    power.criticalDamage() + bonusCritDamage);

            if (power.baseValue() < impact.action.min_power || power.baseValue() > impact.action.max_power) {
                var clampedValue = MathHelper.clamp(power.baseValue(), impact.action.min_power, impact.action.max_power);
                power = new SpellPower.Result(power.school(), clampedValue, power.criticalChance(), power.criticalDamage());
            }

            // Action execution

            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var extraKnockback = 1F;
                    for (var spellModifier: spellModifiers) {
                        extraKnockback += spellModifier.knockback_multiply_base;
                    }

                    var knockbackMultiplier = Math.max(0F, damageData.knockback * context.total() * extraKnockback);
                    var vulnerability = SpellPower.Vulnerability.none;
                    var timeUntilRegen = target.timeUntilRegen;
                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback) livingEntity).pushKnockbackMultiplier_SpellEngine(context.hasOffset() ? 0 : knockbackMultiplier);
                        isKnockbackPushed = true;
                        if (damageData.bypass_iframes && SpellEngineMod.config.bypass_iframes) {
                            target.timeUntilRegen = 0;
                        }
                        vulnerability = SpellPower.getVulnerability(livingEntity, school);
                    }
                    var result = power.random(vulnerability);
                    critical = result.isCritical();
                    var amount = result.amount();
                    amount *= damageData.spell_power_coefficient;
                    amount *= context.total();
                    particleMultiplier = power.criticalDamage() + vulnerability.criticalDamageBonus();

                    ///
                    if (caster instanceof PlayerEntity player) {
                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                    }
                    ///

                    caster.onAttacking(target);
                    var damageSource = SpellDamageSource.create(school, caster);
                    if (critical) {
                        CriticalStrikeCompat.setCriticalStrike(damageSource, (float) power.criticalDamage());
                    }
                    ((DamageSourceExtension)damageSource).setSpellIndirect(context.focusMode() != SpellTarget.FocusMode.DIRECT);
                    target.damage(damageSource, (float) amount);

                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback)livingEntity).popKnockbackMultiplier_SpellEngine();
                        isKnockbackPushed = false;
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
                        var result = power.random();
                        critical = result.isCritical();
                        var amount = result.amount();
                        amount *= healData.spell_power_coefficient;
                        amount *= context.total();
                        if (context.isChanneled()) {
                            amount *= SpellPower.getHaste(caster, school);
                        }
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        livingTarget.heal((float) amount);
                        if (SpellEvents.HEAL.isListened()) {
                            float finalAmount = (float) amount;
                            SpellEvents.HEAL.invoke((listener) -> listener.onHeal(new SpellEvents.HealEvent.Args(caster, spellEntry, livingTarget, finalAmount)));
                        }
                        success = true;
                    }
                }
                case STATUS_EFFECT -> {
                    var data = impact.action.status_effect;
                    if (target instanceof LivingEntity livingTarget) {
                        Optional<RegistryEntry<StatusEffect>> optionalEffect = Optional.empty();
                        if (data.remove != null) {
                            var effects = livingTarget.getStatusEffects()
                                    .stream().filter(instance ->
                                            instance.getEffectType().value().isBeneficial() == data.remove.select_beneficial
                                            && PatternMatching.matches(instance.getEffectType(), RegistryKeys.STATUS_EFFECT, data.remove.id)
                                    )
                                    .toList();
                            if (effects.isEmpty()) {
                                return false;
                            }
                            switch (data.remove.selector) {
                                case RANDOM -> {
                                    optionalEffect = Optional.of(effects.get(world.random.nextInt(effects.size()))).map(StatusEffectInstance::getEffectType);
                                }
                                case FIRST -> {
                                    optionalEffect = Optional.of(effects.getFirst()).map(StatusEffectInstance::getEffectType);
                                }
                            }
                        } else {
                            var id = Identifier.of(data.effect_id);
                            optionalEffect = Optional.of(Registries.STATUS_EFFECT.getEntry(id).get());
                        }
                        if (optionalEffect.isEmpty()) {
                            return false;
                        }
                        var effect = optionalEffect.get();

                        if(!underApplyLimit(power, livingTarget, school, data.apply_limit)) {
                            return false;
                        }
                        var extraDuration = 0F;
                        var extraAmplifier = 0;
                        var extraCap = 0;
                        for (var spellModifier: spellModifiers) {
                            extraDuration += spellModifier.effect_duration_add;
                            extraAmplifier += spellModifier.effect_amplifier_add;
                            extraCap += spellModifier.effect_amplifier_cap_add;
                        }
                        var amplifier = data.amplifier + (int)(data.amplifier_power_multiplier * power.nonCriticalValue());
                        amplifier += extraAmplifier;
                        switch (data.apply_mode) {
                            case ADD, SET -> {
                                if (target.getType().isIn(SpellEngineEntityTags.bosses)
                                        && (StatusEffectClassification.isMovementImpairing(effect) || StatusEffectClassification.disablesMobAI(effect) ) ) {
                                    return false;
                                }
                                var duration = Math.round((data.duration + extraDuration) * 20F);

                                var showParticles = data.show_particles;
                                var cap = data.amplifier_cap
                                        + (int)(data.amplifier_cap_power_multiplier * power.nonCriticalValue())
                                        + extraCap;

                                if (data.apply_mode == Spell.Impact.Action.StatusEffect.ApplyMode.ADD) {
                                    var currentEffect = livingTarget.getStatusEffect(effect);

                                    var increment = amplifier;

                                    int newAmplifier = Math.max(increment - 1, 0);
                                    if (currentEffect != null) {
                                        var currentAmplifier = currentEffect.getAmplifier();
                                        var incrementedAmplifier = currentAmplifier + increment;
                                        newAmplifier = Math.min(incrementedAmplifier, cap);
                                        if (!data.refresh_duration) {
                                            if (currentAmplifier == newAmplifier) {
                                                return false;
                                            }
                                            duration = currentEffect.getDuration();
                                        }
                                    }
                                    amplifier = newAmplifier;
                                } else {
                                    if (cap > 0) {
                                        amplifier = Math.min(amplifier, cap);
                                    }
                                }
                                ///
                                if (caster instanceof PlayerEntity player) {
                                    SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                }
                                ///
                                var instance = new StatusEffectInstance(effect, duration, amplifier, false, showParticles, true);
                                livingTarget.addStatusEffect(instance, caster);
                                success = true;
                            }
                            case REMOVE -> {
                                if (data.amplifier_cap > 0) {
                                    amplifier = Math.min(amplifier, data.amplifier_cap);
                                }
                                if (livingTarget.hasStatusEffect(effect)) {
                                    ///
                                    if (caster instanceof PlayerEntity player) {
                                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                    }
                                    ///
                                    var currentEffect = livingTarget.getStatusEffect(effect);
                                    var newAmplifier = (amplifier > 0) ? (currentEffect.getAmplifier() - amplifier) : -1;
                                    StatusEffectUtil.applyChanges(livingTarget, List.of(new StatusEffectUtil.Diff(currentEffect, newAmplifier)));

                                    success = true;
                                }
                            }
                        }
                    }
                }
                case FIRE -> {
                    ///
                    if (caster instanceof PlayerEntity player) {
                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                    }
                    ///
                    var data = impact.action.fire;
                    target.setOnFireFor(data.duration);
                    if (target.getFireTicks() > 0) {
                        target.setFireTicks(target.getFireTicks() + data.tick_offset);
                    }
                    success = target.isOnFire();
                }
                case SPAWN -> {
                    var spawns = impact.action.spawns;
                    if (spawns == null || spawns.isEmpty()) {
                        return false;
                    }

                    float extraTimeToLive = 0;
                    for (var spellModifier: spellModifiers) {
                        extraTimeToLive += spellModifier.spawn_duration_add;
                    }

                    for(var data: spawns) {
                        var mutableData = data.copy();
                        mutableData.time_to_live_seconds += extraTimeToLive;
                        var id = Identifier.of(mutableData.entity_type_id);
                        var type = Registries.ENTITY_TYPE.get(id);

                        var entity = (Entity)type.create(world);
                        applyEntityPlacement(entity, caster, target.getPos(), mutableData.placement);
                        if (entity instanceof SpellEntity.Spawned spellSpawnedEntity) {
                            var args = new SpellEntity.Spawned.Args(caster, spellEntry, mutableData, context);
                            spellSpawnedEntity.onSpawnedBySpell(args);
                        }
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        ((WorldScheduler)world).schedule(mutableData.delay_ticks, () -> {
                            world.spawnEntity(entity);
                        });
                        success = true;
                    }
                }
                case TELEPORT -> {
                    var data = impact.action.teleport;
                    if (target instanceof LivingEntity livingTarget) {
                        LivingEntity teleportedEntity = null;
                        Vec3d destination = null;
                        Vec3d startingPosition = null;
                        Float applyRotation = null;
                        switch (data.mode) {
                            case FORWARD -> {
                                teleportedEntity = livingTarget;
                                var forward = data.forward;
                                var look = target.getRotationVector();
                                startingPosition = target.getPos();
                                destination = TargetHelper.findTeleportDestination(teleportedEntity, look, forward.distance, data.required_clearance_block_y);
                                var groundJustBelow = TargetHelper.findSolidBlockBelow(teleportedEntity, destination, target.getWorld(), -1.5F);
                                if (groundJustBelow != null) {
                                    destination = groundJustBelow;
                                }
                            }
                            case BEHIND_TARGET -> {
                                if (livingTarget == caster) {
                                    return false;
                                }
                                var look = target.getRotationVector();
                                var distance = 1F;
                                if (data.behind_target != null) {
                                    distance = data.behind_target.distance;
                                }
                                teleportedEntity = caster;
                                startingPosition = caster.getPos();
                                destination = target.getPos().add(look.multiply(-distance));
                                var groundJustBelow = TargetHelper.findSolidBlockBelow(teleportedEntity, destination, target.getWorld(), -1.5F);
                                if (groundJustBelow != null) {
                                    destination = groundJustBelow;
                                }

                                double x = look.x;
                                double z = look.z;
                                // Calculate yaw using arctangent function
                                float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
                                // Normalize yaw to the range [0, 360)
                                yaw = yaw < 0 ? yaw + 360 : yaw;
                                applyRotation = yaw;
                            }
                        }
                        if (destination != null && startingPosition != null && teleportedEntity != null) {
                            ParticleHelper.sendBatches(teleportedEntity, data.depart_particles, false);
                            world.emitGameEvent(GameEvent.TELEPORT, startingPosition, GameEvent.Emitter.of(teleportedEntity));

                            if (applyRotation != null
                                    && teleportedEntity instanceof ServerPlayerEntity serverPlayer
                                    && world instanceof ServerWorld serverWorld) {
                                ///
                                if (caster instanceof PlayerEntity player) {
                                    SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                }
                                ///
                                serverPlayer.teleport(serverWorld, destination.x, destination.y, destination.z, applyRotation, serverPlayer.getPitch());
                                // teleportedEntity.teleport(destination.x, destination.y, destination.z, new HashSet<>(), applyRotation, 0);
                            } else {
                                teleportedEntity.teleport(destination.x, destination.y, destination.z, false);
                            }
                            success = true;

                            ParticleHelper.sendBatches(teleportedEntity, data.arrive_particles, false);
                        }
                    }
                }
                case COOLDOWN -> {
                    var cooldown = impact.action.cooldown;
                    var modified = false;
                    if (cooldown != null && target instanceof PlayerEntity playerTarget) {
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        var cooldownManager = ((SpellCasterEntity)playerTarget).getCooldownManager();
                        if (cooldown.actives != null) {
                            var spells = SpellContainerSource.activeSpellsOf(playerTarget);
                            modified = modified || modifyCooldowns(spells, cooldown.actives, cooldownManager);
                        }
                        if (cooldown.passives != null) {
                            var spells = SpellContainerSource.passiveSpellsOf(playerTarget);
                            modified = modified || modifyCooldowns(spells, cooldown.passives, cooldownManager);
                        }
                        if (modified) {
                            cooldownManager.update(false);
                            cooldownManager.pushSync();
                        }
                    }
                    success = modified;
                }
                case AGGRO -> {
                    if (target instanceof MobEntity mob) {
                        // Ignoring taunt data, as it is empty currently
                        var aggroData = impact.action.aggro;
                        if (aggroData == null) {
                            return false;
                        }
                        if (aggroData.only_if_targeted && mob.getTarget() != caster) {
                            return false; // Only taunt if the mob is already targeting the caster
                        }
                        // mob.setTarget(tauntData.reverse ? null : caster);
                        switch (aggroData.mode) {
                            case SET -> {
                                mob.setTarget(caster);
                            }
                            case CLEAR -> {
                                mob.setTarget(null);
                            }
                        }
                        success = true;
                    }
                }
                case DISRUPT -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var disrupt = impact.action.disrupt;
                        if (target instanceof PlayerEntity playerTarget) {
                             if (disrupt.shield_blocking && playerTarget.isBlocking()) {
                                 playerTarget.disableShield();
                                 success = true;
                             } else if (disrupt.item_usage_seconds > 0 && playerTarget.isUsingItem()) {
                                 var activeStack = playerTarget.getActiveItem();
                                 playerTarget.getItemCooldownManager().set(activeStack.getItem(), (int) (disrupt.item_usage_seconds * 20F));
                                 success = true;
                             }
                        } else {
                            if (disrupt.shield_blocking && livingTarget.isBlocking()) {
                                livingTarget.clearActiveItem();
                                success = true;
                            } else if (disrupt.item_usage_seconds > 0 && livingTarget.isUsingItem()) {
                                livingTarget.clearActiveItem();
                                success = true;
                            }
                        }
                    }
                }
                case IMMUNITY -> {
                    var data = impact.action.immunity;
                    if (target instanceof LivingEntity livingTarget
                            && impact.action.immunity != null) {
                        DamageType type = null;
                        TagKey<DamageType> typeTagKey = null;
                        if (data.damage_type != null) {
                            if (data.damage_type.startsWith(PatternMatching.TAG_PREFIX)) {
                                var id = Identifier.of(data.damage_type.substring(PatternMatching.TAG_PREFIX.length()));
                                typeTagKey = TagKey.of(RegistryKeys.DAMAGE_TYPE, id);
                            } else {
                                var id = Identifier.of(data.damage_type);
                                var registry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE);
                                type = registry.get(id);
                            }
                        }
                        if (data.duration_ticks > 0) {
                            LivingEntityImmunity.apply(livingTarget, type, typeTagKey, data.damage_indirect, data.duration_ticks);
                            success = true;
                        }
                    }
                }
                case CUSTOM -> {
                    if (impact.action.custom != null) {
                        var handler = SpellHandlers.customImpact.get(impact.action.custom.handler);
                        if (handler != null) {
                            ///
                            if (caster instanceof PlayerEntity player) {
                                SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                            }
                            ///
                            var result = handler.onSpellImpact(spellEntry, power, caster, target, context);
                            particleMultiplier = power.criticalDamage();
                            success = result.success();
                            critical = result.critical();
                        }
                    }
                }
            }
            if (success) {
                if (impact.particles != null) {
                    float countMultiplier = critical ? (float) particleMultiplier : 1F;
                    ParticleHelper.sendBatches(target, impact.particles, countMultiplier * caster.getScale(), trackers);
                }
                if (impact.sound != null) {
                    SoundHelper.playSound(world, target, impact.sound);
                }
                if (targetWasAlive && caster instanceof PlayerEntity player) {
                    var finalTarget = target;
                    var finalCritical = critical;
                    ((WorldScheduler)world).schedule(0, () -> {
                        SpellTriggers.onSpellImpactSpecific(player, finalTarget, spellEntry, impact, finalCritical, Spell.Trigger.Stage.POST);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to perform impact effect");
            System.err.println(e.getMessage());
            if (isKnockbackPushed) {
                ((ConfigurableKnockback)target).popKnockbackMultiplier_SpellEngine();
            }
        }
        return success;
    }

    private static boolean modifyCooldowns(List<RegistryEntry<Spell>> spells, Spell.Impact.Action.Cooldown.Modify modifier, SpellCooldownManager cooldownManager) {
        var modifiedAny = false;
        for (var spell: spells) {
            var id = spell.getKey().get().getValue();
            if (PatternMatching.matches(spell, SpellRegistry.KEY, modifier.id)) {
                var duration = cooldownManager.getCooldownDuration(spell);
                int updatedDuration = (int) ((duration + modifier.duration_add) * modifier.duration_multiplier);
                if (updatedDuration != duration) {
                    cooldownManager.setDurationLeft(spell, updatedDuration);
                    modifiedAny = true;
                }
            }
        }
        return modifiedAny;
    }

    public record TargetConditionResult(boolean allowed, List<Spell.Impact.Modifier> modifiers) {
        public static final TargetConditionResult ALLOWED = new TargetConditionResult(true, List.of());
        public static final TargetConditionResult DENIED = new TargetConditionResult(false, List.of());
    }
    public static TargetConditionResult evaluateImpactConditions(Entity target, LivingEntity caster, List<Spell.Impact.TargetModifier> target_modifiers) {
        if (target_modifiers == null) {
            return TargetConditionResult.ALLOWED;
        }
        var modifiers = new ArrayList<Spell.Impact.Modifier>();
        for (var entry: target_modifiers) {
            var conditionMet = true;
            var i = 0;
            for (var condition: entry.conditions) {
                var newResult = SpellTarget.evaluate(target, caster, condition);
                if (i == 0) {
                    conditionMet = newResult;
                } else {
                    conditionMet = entry.all_required
                            ? conditionMet && newResult
                            : conditionMet || newResult;
                }
                i += 1;
            }
            switch (entry.execute) {
                case ALLOW -> {
                    if (!conditionMet) {
                        return TargetConditionResult.DENIED;
                    }
                }
                case DENY -> {
                    if (conditionMet) {
                        return TargetConditionResult.DENIED;
                    }
                }
            }
            if (conditionMet) {
                if (entry.modifier != null) {
                    modifiers.add(entry.modifier);
                }
            }
        }
        return new TargetConditionResult(true, modifiers);
    }

    public static void placeCloud(World world, LivingEntity caster,
                                  @Nullable Entity target, @Nullable Vec3d location,
                                  RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var spell = spellEntry.value();
        var clouds = spell.deliver.clouds;
        if (clouds == null || clouds.isEmpty()) {
            return;
        }
        if (target == null && location == null) {
            target = caster;
        }

        List<Spell.Modifier> spellModifiers = List.of();
        if (caster instanceof PlayerEntity player) {
            spellModifiers = SpellModifiers.of(player, spellEntry);
        }
        float extraTimeToLive = 0;
        var extraPlacements = new ArrayList<Spell.EntityPlacement>();
        for (var spellModifier: spellModifiers) {
            extraTimeToLive += spellModifier.spawn_duration_add;
            extraPlacements.addAll(spellModifier.additional_placements);
        }

        var index = 0;
        for (var cloud: clouds) {
            var placements = new ArrayList<Spell.EntityPlacement>();
            placements.add(cloud.placement);
            placements.addAll(cloud.additional_placements);
            if (index == 0) {
                placements.addAll(extraPlacements);
            }
            var base_delay = cloud.delay_ticks;

            for (var placement: placements) {
                var delay = base_delay + placement.delay_ticks;

                SpellCloud entity;
                if (cloud.entity_type_id != null) {
                    var id = Identifier.of(cloud.entity_type_id);
                    var type = Registries.ENTITY_TYPE.get(id);
                    entity = (SpellCloud) type.create(world);
                } else {
                    entity = new SpellCloud(world);
                }
                entity.setOwner(caster);
                entity.onCreatedFromSpell(spellEntry.getKey().get().getValue(), cloud, context, cloud.time_to_live_seconds + extraTimeToLive);

                if (target != null) {
                    applyEntityPlacement(entity, target, target.getPos(), placement);
                } else if (location != null) {
                    applyEntityPlacement(caster.getWorld(), entity,
                            caster.getYaw(), caster.getPitch(), null,
                            location, placement);
                } else {
                    continue;
                }


                ((WorldScheduler)world).schedule(delay, () -> {
                    world.spawnEntity(entity);
                    var sound = cloud.spawn.sound;
                    if (sound != null) {
                        SoundHelper.playSound(world, entity, sound);
                    }
                    var particles = cloud.spawn.particles;
                    if (particles != null) {
                        ParticleHelper.sendBatches(entity, particles);
                    }
                });

                if (cloud.placement_delay_stacks) {
                    base_delay = delay;
                }
            }
            index += 1;
        }
    }

    public static void applyEntityPlacement(Entity entity, Entity target, Vec3d initialPosition, Spell.EntityPlacement placement) {
        applyEntityPlacement(target.getWorld(), entity, target.getYaw(), target.getPitch(), target, initialPosition, placement);
    }

    public static void applyEntityPlacement(World world, Entity placedEntity,
                                            float targetedYaw, float targetedPitch, @Nullable Entity rayCastEntity,
                                            Vec3d initialPosition, Spell.EntityPlacement placement) {
        var position = initialPosition;
        if (placement != null) {
            if (placement.location_offset_by_look > 0) {
                float yaw = targetedYaw + placement.location_yaw_offset;
                position = position.add(Vec3d.fromPolar(0, yaw).multiply(placement.location_offset_by_look));
            }
            position = position.add(new Vec3d(placement.location_offset_x, placement.location_offset_y, placement.location_offset_z));
            if (placement.force_onto_ground) {
                var searchPosition = position;
                var blockPos = BlockPos.ofFloored(searchPosition.getX(), searchPosition.getY(), searchPosition.getZ());
                if (world.getBlockState(blockPos).isSolid()) {
                    searchPosition = searchPosition.add(0, 2, 0);
                }
                var groundPosBelow = TargetHelper.findSolidBlockBelow(rayCastEntity, searchPosition, world, -20);
                position = groundPosBelow != null ? groundPosBelow : position;
            }
            if (placement.apply_yaw) {
                placedEntity.setYaw(targetedYaw);
            }
            if (placement.apply_pitch) {
                placedEntity.setPitch(targetedPitch);
            }
            position = position.add(new Vec3d(placement.location_offset_x, placement.location_offset_y, placement.location_offset_z));
        }
        placedEntity.setPosition(position.getX(), position.getY(), position.getZ());
    }

    public static SpellTarget.FocusMode focusMode(Spell spell) {
        switch (spell.target.type) {
            case AREA, BEAM -> {
                return SpellTarget.FocusMode.AREA;
            }
            case NONE, CASTER, AIM, FROM_TRIGGER -> {
                return SpellTarget.FocusMode.DIRECT;
            }
        }
        assert true;
        return null;
    }

    public static Optional<SpellTarget.Intent> deliveryIntent(Spell spell) {
        switch (spell.deliver.type) {
            case STASH_EFFECT -> {
                var intent = intentForStatusEffect(spell.deliver.stash_effect.id);
                return Optional.of(intent);
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    public static EnumSet<SpellTarget.Intent> impactIntents(Spell spell) {
        var intents = new HashSet<SpellTarget.Intent>();
        for (var impact: spell.impacts) {
            intents.add(impactIntent(impact.action));
            //return intent(impact.action);
        }
        return EnumSet.copyOf(intents);
    }

    public static SpellTarget.Intent impactIntent(Spell.Impact.Action action) {
        switch (action.type) {
            case DAMAGE, FIRE, AGGRO, DISRUPT -> {
                return SpellTarget.Intent.HARMFUL;
            }
            case HEAL, IMMUNITY -> {
                return SpellTarget.Intent.HELPFUL;
            }
            case STATUS_EFFECT -> {
                if (action.status_effect.remove != null) {
                    return action.status_effect.remove.select_beneficial ? SpellTarget.Intent.HARMFUL : SpellTarget.Intent.HELPFUL;
                }
                return intentForStatusEffect(action.status_effect.effect_id);
            }
            case SPAWN -> {
                var intent = SpellTarget.Intent.HELPFUL;
                if (!action.spawns.isEmpty()) {
                    intent = action.spawns.getFirst().intent;
                }
                return intent;
            }
            case TELEPORT -> {
                return action.teleport.intent;
            }
            case COOLDOWN -> {
                var cooldown = action.cooldown;
                if (cooldown != null) {
                    var duration_add = 0F;
                    var duration_multiplier = 1F;
                    if (cooldown.actives != null) {
                        duration_add += cooldown.actives.duration_add;
                        duration_multiplier += cooldown.actives.duration_multiplier - 1;
                    }
                    if (cooldown.passives != null) {
                        duration_add += cooldown.passives.duration_add;
                        duration_multiplier += cooldown.passives.duration_multiplier - 1;
                    }
                    var addHelpful = duration_add <= 0;
                    var multiplierHelpful = duration_multiplier <= 1;
                    return addHelpful && multiplierHelpful ? SpellTarget.Intent.HELPFUL : SpellTarget.Intent.HARMFUL;
                }
                return SpellTarget.Intent.HELPFUL;
            }
            case CUSTOM -> {
                return action.custom.intent;
            }
        }
        assert true;
        return null;
    }

    private static SpellTarget.Intent intentForStatusEffect(String idString) {
        var id = Identifier.of(idString);
        var effect = Registries.STATUS_EFFECT.get(id);
        return effect.isBeneficial() ? SpellTarget.Intent.HELPFUL : SpellTarget.Intent.HARMFUL;
    }

    public static boolean underApplyLimit(SpellPower.Result spellPower, LivingEntity target, SpellSchool school, Spell.Impact.Action.StatusEffect.ApplyLimit limit) {
        if (limit == null) {
            return true;
        }
        var power = (float) spellPower.nonCriticalValue();
        float cap = limit.health_base + (power * limit.spell_power_multiplier);
        return cap >= target.getMaxHealth();
    }

    // DAMAGE/HEAL OUTPUT ESTIMATION

    public static EstimatedOutput estimate(Spell spell, PlayerEntity caster, ItemStack itemStack) {
        var spellSchool = spell.school;
        var damageEffects = new ArrayList<EstimatedValue>();
        var healEffects = new ArrayList<EstimatedValue>();
        var isEquipped = AttributeModifierUtil.isItemStackEquipped(itemStack, caster);
        ArrayList<Spell.Impact> impacts = new ArrayList<>(spell.impacts);
        if (spell.modifiers != null) {
            for (var modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
            }
        }

        for (var impact: impacts) {
            var school = impact.school != null ? impact.school : spellSchool;
            var attribute = school.attributeEntry;
            boolean attributeOverride = false;
            if (impact.attribute != null && !impact.attribute.isEmpty()) {
                var optionalAttribute = Registries.ATTRIBUTE.getEntry(Identifier.of(impact.attribute));
                if (optionalAttribute.isPresent()) {
                    attribute = optionalAttribute.get();
                    attributeOverride = true;
                }
            }

            var flatBonusOnItemStack = AttributeModifierUtil.flatBonusFrom(itemStack, attribute);
            /// It would be best to have some information here about the context
            /// whether the spell tooltip is generated for a cache, or for a player initiated tooltip
            boolean useRealAttributes = isEquipped || flatBonusOnItemStack == 0;

            SpellPower.Result power;
            if (useRealAttributes) {
                power = SpellPower.getSpellPower(school, caster);
                if (attributeOverride) {
                    var value = caster.getAttributeValue(attribute);
                    power = new SpellPower.Result(school, value, power.criticalChance(), power.criticalDamage());
                }
            } else {
                power = new SpellPower.Result(school, flatBonusOnItemStack, 0, 1F);
            }
            if (power.baseValue() < impact.action.min_power || power.baseValue() > impact.action.max_power) {
                var clampedValue = MathHelper.clamp(power.baseValue(), impact.action.min_power, impact.action.max_power);
                power = new SpellPower.Result(power.school(), clampedValue, power.criticalChance(), power.criticalDamage());
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
            }
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