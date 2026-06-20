package net.spell_engine.api.spell.summon;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.spell_engine.api.entity.TwoWayCollisionChecker;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellCooldownManager;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.target.EntityRelation;
import net.spell_engine.internals.target.EntityRelations;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class SummonedEntity extends GolemEntity implements SpellSummoned, Tameable {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    public static final TrackedData<Byte> PHASE =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.BYTE);
    public static final TrackedData<Byte> COLLISION_MODE =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.BYTE);
    public static final TrackedData<Float> BOUNDING_BOX_WIDTH =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Float> BOUNDING_BOX_HEIGHT =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<Integer> END_OF_PHASE_AGE =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.INTEGER);
    // One packed tracker per action type — kept separate so a spell cast and a melee swing
    // can animate in parallel without one stomping the other's state.
    //
    // Packed layout (same for all three):
    //   bits  0..7   variant   (0..255)
    //   bits  8..23  duration  (ticks; 0 = inactive)
    //   bits 24..55  startAge  (entity age when the action began)
    //
    // Why packed (instead of three primitive trackers per descriptor): DataTracker.set()
    // silently drops no-op writes (value equals current → not dirty → not synced → client
    // onTrackedDataSet never fires). When swings chain (target dies mid-swing, new target
    // acquired the same tick), a plain action/duration tracker could re-set to the same
    // value and skip the packet, leaving the animation desynced. Including the monotonic
    // startAge in the same long guarantees every action start changes the value, forcing
    // a sync.
    public static final TrackedData<Long> ATTACK_ANIMATION =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.LONG);
    public static final TrackedData<Long> SPELL_CAST_ANIMATION =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.LONG);
    public static final TrackedData<Long> SPELL_RELEASE_ANIMATION =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.LONG);
    // Compact, one-time-synced descriptor of the behaviour's existence particles (Gson JSON). The
    // client parses it once and spawns the particles locally each interval (see tick()), so
    // continuous ambient particles cost no per-tick network traffic. Empty = none.
    public static final TrackedData<String> EXISTENCE_PARTICLES =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.STRING);

    private static long packAnim(int variant, int duration, int startAge) {
        return ((long)(variant  & 0xFF))
             | (((long)(duration & 0xFFFF)) << 8)
             | ((((long) startAge) & 0xFFFFFFFFL) << 24);
    }
    private static int animVariant(long v)  { return (int)  (v        & 0xFF); }
    private static int animDuration(long v) { return (int) ((v >>> 8)  & 0xFFFF); }
    private static int animStartAge(long v) { return (int)  (v >>> 24); }

    // Sentinel duration meaning "runs until an explicit stop is sent" (e.g., a spell cast,
    // whose duration isn't known up front). Max value of the 16-bit duration field.
    private static final int DURATION_ENDLESS = 0xFFFF;

    private static final byte PHASE_SPAWNING   = 0;
    private static final byte PHASE_ACTIVE     = 1;
    private static final byte PHASE_DESPAWNING = 2;

    private int timeToLive = 0;
    private int spawnEndAge = 0;
    private int despawnStartAge = 0;
    @Nullable protected SummonBehaviour behaviour = null;

    public SummonedEntity(EntityType<? extends SummonedEntity> entityType, World world) {
        super(entityType, world);
        // Eagerly start the spawn AnimationState so the very first render frame already
        // has the spawn animation's t=0 keyframes applied. Otherwise the renderer can
        // run once before tick()/setupAnimationStates() — AnimationState.run() no-ops
        // because the state isn't running yet, AnimationHelper.animate() leaves the
        // resetTransform() defaults in place, and the model briefly flashes at full
        // size/pose before the scale-from-0 spawn animation takes over.
        // Safe for the loaded-from-NBT (non-spawning) case: the next setupAnimationStates()
        // call stops the state via setRunning(isSpawning(), age) → stop().
        spawnAnimationState.startIfNotRunning(0);
    }

    @Override
    public EntityDimensions getBaseDimensions(EntityPose pose) {
        float w = getDataTracker().get(BOUNDING_BOX_WIDTH);
        float h = getDataTracker().get(BOUNDING_BOX_HEIGHT);
        // 0 (or anything <= 0) = "no override is configured" — defer to vanilla, which
        // returns type.getDimensions().scaled(getScaleFactor()) (handles baby scale etc.).
        if (w <= 0 || h <= 0) return super.getBaseDimensions(pose);
        // `changing` (fixed=false) is required: `EntityDimensions.scaled()` short-circuits
        // and returns `this` unchanged when `fixed=true`, which would silently swallow the
        // GENERIC_SCALE attribute multiplier vanilla applies in LivingEntity.getDimensions.
        return EntityDimensions.changing(w, h);
    }

    @Override
    public boolean isPushable() {
        return collisionMode() != SummonBehaviour.Movement.CollisionMode.NONE;
        // return (behaviour == null || behaviour.movement.is_pushable) && super.isPushable();
    }

    @Override
    public boolean isAttackable() {
        return (behaviour == null || behaviour.is_attackable) && super.isAttackable();
    }

    @Override
    public boolean canHit() {
        return (behaviour == null || behaviour.is_attackable) && super.canHit();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return (behaviour == null || behaviour.is_attackable) && super.damage(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return (behaviour == null || !behaviour.is_attackable) && super.isInvulnerableTo(damageSource);
    }

    @Override
    public boolean isInvulnerable() {
        // Report a non-attackable summon as invulnerable so every system that gates on the
        // getter treats it as a non-target — most importantly LivingEntity.canTakeDamage()
        // (`!isInvulnerable()`), which vanilla and modded mob target acquisition use. This
        // makes the whole world ignore it, not just our own shouldTarget/canAttackTarget.
        //
        // Safe by construction: vanilla isInvulnerableTo() and the /kill, void and despawn
        // paths read the `invulnerable` *field* (still false), not this getter, so the
        // entity stays removable; and NBT persists the field, so nothing round-trips. Actual
        // damage immunity remains enforced by the damage()/isInvulnerableTo() overrides.
        if (behaviour != null && !behaviour.is_attackable) return true;
        return super.isInvulnerable();
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return (behaviour == null || !behaviour.is_attackable) && super.isImmuneToExplosion(explosion);
    }

    public void takeKnockback(double strength, double x, double z) {
        if (behaviour != null && !behaviour.is_attackable) {
            return;
        }
        super.takeKnockback(strength, x, z);
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        if (behaviour != null) {
            SoundEvent custom = behaviour.sounds.hurtEvent.get();
            if (custom != null) return custom;
        }
        return super.getHurtSound(source);
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        if (behaviour != null) {
            SoundEvent custom = behaviour.sounds.deathEvent.get();
            if (custom != null) return custom;
        }
        return super.getDeathSound();
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        if (behaviour != null) {
            SoundEvent custom = behaviour.sounds.ambientEvent.get();
            if (custom != null) return custom;
        }
        return super.getAmbientSound();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (behaviour != null) {
            SoundEvent custom = behaviour.sounds.stepEvent.get();
            if (custom != null) {
                // Mirrors the vanilla footstep volume scaling (15% of normal) so the sound
                // doesn't dominate while the entity walks.
                this.playSound(custom, 0.15F, 1.0F);
                return;
            }
        }
        super.playStepSound(pos, state);
    }

    /// Broadcasts a configured sound from this entity's position. Silent when `sound` is null.
    public void playConfiguredSound(@Nullable SoundEvent sound) {
        if (sound == null) return;
        getWorld().playSound(null, getX(), getY(), getZ(),
                sound, getSoundCategory(), 1.0F, 1.0F);
    }

    private SummonBehaviour.Movement.CollisionMode collisionMode() {
        return SummonBehaviour.Movement.CollisionMode.values()[getDataTracker().get(COLLISION_MODE)];
    }

    @Override
    public boolean isCollidable() {
        if (collisionMode() == SummonBehaviour.Movement.CollisionMode.NONE) return false;
        return super.isCollidable();
    }

    @Override
    public boolean collidesWith(Entity other) {
        switch (collisionMode()) {
            case NONE -> { return false; }
            case ALL  -> { return super.collidesWith(other); }
            case ENEMIES -> {
                var collidesAccordingToRelation = false;
                if (getOwner() != null) {
                    var relation = EntityRelations.getRelation(getOwner(), other);
                    collidesAccordingToRelation = relation == EntityRelation.HOSTILE || relation == EntityRelation.NEUTRAL;
                }
                return super.collidesWith(other) && collidesAccordingToRelation;
            }
        }
        return super.collidesWith(other);
    }

    // Stops tickCramming() from pushing other entities through player.pushAwayFrom(this).
    // Both client and server call pushAway(), so the DataTracker-synced collisionMode() is enough.
    @Override
    protected void pushAway(Entity entity) {
        if (collisionMode() != SummonBehaviour.Movement.CollisionMode.NONE) {
            super.pushAway(entity);
        }
    }

    // Mirrors BarrierEntity's constructor pattern: install a TwoWayCollisionChecker reverseCollisionChecker
    // so SpellEngine's EntityCollision mixin also lets everything pass through this entity.
    // Runs on both sides: server when setBehaviour sets the value, client when the DataTracker update arrives.
    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data.equals(BOUNDING_BOX_WIDTH) || data.equals(BOUNDING_BOX_HEIGHT)) {
            calculateDimensions();
        }
        if (data.equals(COLLISION_MODE)) {
            if (collisionMode() == SummonBehaviour.Movement.CollisionMode.NONE) {
                ((TwoWayCollisionChecker) this).setReverseCollisionChecker(
                        entity -> TwoWayCollisionChecker.CollisionResult.PASS
                );
            } else {
                ((TwoWayCollisionChecker) this).setReverseCollisionChecker(null);
            }
        }
        if (data.equals(ATTACK_ANIMATION)) {
            syncActionAnimationState(attackAnimationState, ATTACK_ANIMATION);
        } else if (data.equals(SPELL_CAST_ANIMATION)) {
            syncActionAnimationState(spellCastAnimationState, SPELL_CAST_ANIMATION);
        } else if (data.equals(SPELL_RELEASE_ANIMATION)) {
            syncActionAnimationState(spellReleaseAnimationState, SPELL_RELEASE_ANIMATION);
        } else if (data.equals(EXISTENCE_PARTICLES)) {
            parseExistenceParticles();
        }
    }

    // Client-side cache of the existence-particle config, parsed once from the synced descriptor.
    @Nullable private SummonBehaviour.ExistenceParticles[] clientExistenceParticles = null;

    private void parseExistenceParticles() {
        var json = getDataTracker().get(EXISTENCE_PARTICLES);
        if (json == null || json.isEmpty()) {
            clientExistenceParticles = null;
            return;
        }
        try {
            clientExistenceParticles = GSON.fromJson(json, SummonBehaviour.ExistenceParticles[].class);
        } catch (Exception e) {
            clientExistenceParticles = null;
        }
    }

    /// Server-side: emits the individual spawn FX once, when the entity enters the world. Particles
    /// go out as a tracker packet, model effects as self-syncing entities, both at this entity.
    private void emitSpawnFx() {
        if (behaviour == null || behaviour.spawn_fx == null) return;
        var fx = behaviour.spawn_fx;
        var world = getWorld();
        if (fx.particles != null && fx.particles.length > 0) {
            ParticleHelper.sendBatches(this, fx.particles);
        }
        ModelEffectHelper.spawn(world, getPos(), getYaw(), fx.model_fx, this);
    }

    /// Client-side: spawns the configured existence particles locally on their interval, during the
    /// ACTIVE phase. No network traffic — the config was synced once via EXISTENCE_PARTICLES.
    private void spawnExistenceParticles() {
        if (clientExistenceParticles == null || !isActive()) return;
        var world = getWorld();
        for (var ep : clientExistenceParticles) {
            if (ep == null || ep.particles == null || ep.particles.length == 0 || ep.interval_ticks <= 0) {
                continue;
            }
            if (((this.age - ep.offset_ticks) % ep.interval_ticks) == 0) {
                ParticleHelper.play(world, this, ep.particles);
            }
        }
    }

    private void syncActionAnimationState(AnimationState state, TrackedData<Long> descriptor) {
        if (animDuration(getDataTracker().get(descriptor)) > 0) {
            state.start(age);
        } else {
            state.stop();
        }
    }

    public boolean isSpawning()   { return getDataTracker().get(PHASE) == PHASE_SPAWNING; }
    public boolean isDespawning() { return getDataTracker().get(PHASE) == PHASE_DESPAWNING; }
    public boolean isActive()     { return getDataTracker().get(PHASE) == PHASE_ACTIVE; }
    private void setPhase(byte phase) {
        byte previous = getDataTracker().get(PHASE);
        if (previous != phase && phase == PHASE_DESPAWNING && behaviour != null) {
            playConfiguredSound(behaviour.sounds.despawnEvent.get());
        }
        getDataTracker().set(PHASE, phase);
        int endAge = switch (phase) {
            case PHASE_SPAWNING   -> spawnEndAge;
            case PHASE_ACTIVE     -> despawnStartAge;
            case PHASE_DESPAWNING -> timeToLive;
            default               -> 0;
        };
        getDataTracker().set(END_OF_PHASE_AGE, endAge);
    }

    @Override
    public void onSummonedBySpell(SpellSummoned.Args args) {
        var ls = args.behaviour.lifespan;
        this.spawnEndAge     = ls.spawn_ticks;
        this.timeToLive      = ls.spawn_ticks + ls.active_seconds * 20 + ls.despawn_ticks;
        this.despawnStartAge = this.timeToLive - ls.despawn_ticks;
        setOwnerUuid(args.owner.getUuid());
        setBehaviour(args.behaviour);
        // Defer to the first server tick: callers like WizardEntities run
        //   onSummonedBySpell() → setPos() → spawnEntity()
        // so playing here broadcasts from the entity's default (0,0,0) position. Setting
        // this flag fires the sound on the next tick(), by which point setPos has run and
        // the entity is in the world. NBT-loaded entities go through readCustomDataFromNbt
        // and skip this path, so they don't re-play the spawn sound on chunk reload.
        pendingSpawnSound = true;
    }

    private boolean pendingSpawnSound = false;

    private void setBehaviour(SummonBehaviour behaviour) {
        if (this.behaviour != null) return;
        this.behaviour = behaviour;
        this.initGoals();
        LivingEntity owner = getOwner();
        if (owner != null) {
            this.applyAttributeScaling(owner);
        }
        getDataTracker().set(COLLISION_MODE, (byte) behaviour.movement.collision.ordinal());
        // Sync the existence-particle config once so the client can spawn them locally (no per-tick
        // packets). Only the particle FX travels — not the whole behaviour.
        if (!behaviour.existence_particles.isEmpty()) {
            getDataTracker().set(EXISTENCE_PARTICLES, GSON.toJson(behaviour.existence_particles));
        }
        // Dimensions are EntityType-seeded in initDataTracker. Only override when the
        // behaviour explicitly carries a non-null Dimensions block.
        if (behaviour.dimensions != null) {
            getDataTracker().set(BOUNDING_BOX_WIDTH,  behaviour.dimensions.width);
            getDataTracker().set(BOUNDING_BOX_HEIGHT, behaviour.dimensions.height);
        }
        calculateDimensions();
        if (!behaviour.movement.affected_by_gravity) {
            this.setNoGravity(true);
        }
        if (!behaviour.movement.is_pushable) {
            this.getAttributeInstance(EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE).addTemporaryModifier(new EntityAttributeModifier(Identifier.of("unpushable"), 9999, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void applyAttributeScaling(LivingEntity owner) {
        if (behaviour == null) return;
        var healthRatio = this.getHealth() / this.getMaxHealth();
        for (var entry : behaviour.attribute_scaling.entries) {
            var targetAttrOpt = Registries.ATTRIBUTE.getEntry(Identifier.of(entry.attribute_id));
            if (targetAttrOpt.isEmpty()) continue;
            var instance = this.getAttributeInstance(targetAttrOpt.get());
            if (instance == null) continue;

            double bonus = 0;
            for (var modifier : entry.modifiers) {
                var ownerAttrOpt = Registries.ATTRIBUTE.getEntry(Identifier.of(modifier.attribute_id));
                if (ownerAttrOpt.isEmpty()) continue;
                var ownerInstance = owner.getAttributeInstance(ownerAttrOpt.get());
                if (ownerInstance == null) continue;
                bonus += modifier.base + ownerInstance.getValue() * modifier.coefficient;
            }

            var modifierId = Identifier.of(SpellEngineMod.ID, "summon_scaling/" + entry.attribute_id.replace(":", "/"));
            instance.removeModifier(modifierId);
            instance.addTemporaryModifier(new EntityAttributeModifier(modifierId, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        this.setHealth(this.getMaxHealth() * healthRatio);
    }

    @Override
    protected void initGoals() {
        if (behaviour == null) return;

        // --- Goal selector ---

        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new PhaseBlockGoal());
        // Teleport is intentionally ordered above action goals so it preempts an in-progress
        // melee swing or spell cast. Walk-follow stays below them (added later, priority
        // after FaceTargetGoal) so normal catch-up doesn't interrupt active combat.
        if (behaviour.movement.can_move && behaviour.movement.follow != null) {
            goalSelector.add(2, new TeleportToSummonerGoal());
        }
        int actionPriority = 10;
        for (var action : behaviour.actions) {
            switch (action.type) {
                case MELEE_ATTACK -> goalSelector.add(actionPriority, new DynamicMeleeAttackGoal(this, action.melee_attack));
                case SPELL_CAST -> goalSelector.add(actionPriority, new SpellCastGoal(action.spell_cast));
            }
            actionPriority++;
        }
        int priority = actionPriority;
        goalSelector.add(priority++, new FaceTargetGoal());
        var movement = behaviour.movement;
        if (movement.can_move) {
            if (movement.follow != null) {
                goalSelector.add(priority++, new FollowSummonerGoal());
            }
            goalSelector.add(priority++, new WanderWhenIdleGoal(movement.wander.speed, movement.wander.probability));
        }
        if (behaviour.targeting.look_around) {
            goalSelector.add(priority++, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
            goalSelector.add(priority, new LookAroundGoal(this));
        }

        // --- Target selector ---

        if (behaviour.targeting.attack_with_owner) {
            targetSelector.add(1, new DefendOwnerGoal());
            targetSelector.add(3, new MirrorOwnerAttackGoal());
        }
        if (behaviour.targeting.revenge) {
            targetSelector.add(2, new ClosestAttackerRevengeGoal());
        }
        // Friendly goal added first (lower priority number) so wounded-ally healing takes
        // precedence over hostile acquisition when BOTH is configured.
        switch (behaviour.targeting.automatic_targeting) {
            case FRIENDLY -> targetSelector.add(4, new DetectionRangeTargetGoal<>(LivingEntity.class, 10, true, false, this::shouldHealTarget));
            case HOSTILE  -> targetSelector.add(4, new DetectionRangeTargetGoal<>(MobEntity.class,    10, true, false, this::shouldTarget));
            case BOTH     -> {
                targetSelector.add(4, new DetectionRangeTargetGoal<>(LivingEntity.class, 10, true, false, this::shouldHealTarget));
                targetSelector.add(5, new DetectionRangeTargetGoal<>(MobEntity.class,    10, true, false, this::shouldTarget));
            }
            case NONE     -> { /* no auto-acquisition */ }
        }
    }

    private boolean shouldTarget(LivingEntity candidate) {
        LivingEntity owner = getOwner();
        if (owner == null) return false;
        if (candidate == owner) return false;
        if (candidate instanceof Tameable t && owner.getUuid().equals(t.getOwnerUuid())) return false;
        return EntityRelations.getRelation(owner, candidate) == EntityRelation.HOSTILE;
    }

    private boolean shouldHealTarget(LivingEntity candidate) {
        LivingEntity owner = getOwner();
        if (owner == null) return false;
        if (candidate == this) return false;
        // Only wounded entities are heal targets — otherwise the goal would lock onto
        // a full-health ally and the heal action would burn cooldowns on no-ops.
        if (candidate.getHealth() >= candidate.getMaxHealth()) return false;
        if (candidate == owner) return true;
        if (candidate instanceof Tameable t && owner.getUuid().equals(t.getOwnerUuid())) return true;
        return EntityRelations.getRelation(owner, candidate) == EntityRelation.FRIENDLY;
    }

    public boolean canAttackTarget(@Nullable LivingEntity target, LivingEntity owner) {
        if (target == null) return false;
        EntityRelation relation = EntityRelations.getRelation(owner, target);
        return relation == EntityRelation.HOSTILE || relation == EntityRelation.NEUTRAL;
    }

    /// Auto-target acquisition/retention range (blocks), resolved from the behaviour's
    /// configured detection-range mode. Falls back to GENERIC_FOLLOW_RANGE for the
    /// FOLLOW_RANGE mode, a null config (e.g. legacy NBT), or a MAXIMUM_ACTION_RANGE that
    /// resolves to nothing.
    private double detectionRange() {
        double followRange = getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        var config = behaviour != null ? behaviour.targeting.detection_range : null;
        if (config == null) return followRange;
        return switch (config.mode) {
            case FOLLOW_RANGE -> followRange;
            case STATIC -> config.value;
            case MAXIMUM_ACTION_RANGE -> {
                double max = maximumActionRange();
                yield max > 0 ? max : followRange;
            }
        };
    }

    /// Largest effective range across all configured actions: spell effective ranges
    /// (`SpellHelper.getRange` × the action's `range.max` fraction) and melee reach
    /// (`max_range` scaled by the entity's size). 0 when no action yields a positive range.
    private double maximumActionRange() {
        if (behaviour == null) return 0;
        double max = 0;
        for (var action : behaviour.actions) {
            double r = switch (action.type) {
                case MELEE_ATTACK -> meleeActionRange(action.melee_attack);
                case SPELL_CAST   -> spellActionRange(action.spell_cast);
            };
            if (r > max) max = r;
        }
        return max;
    }

    private double meleeActionRange(SummonBehaviour.Action.MeleeAttack melee) {
        if (melee == null || melee.max_range <= 0) return 0;
        // Mirrors DynamicMeleeAttackGoal.effectiveMaxRange (scales reach with entity size).
        return melee.max_range * (1 + getScale() * melee.attack_range_scaling);
    }

    private double spellActionRange(SummonBehaviour.Action.SpellCast spell) {
        if (spell == null) return 0;
        var entry = SpellRegistry.from(getWorld()).getEntry(Identifier.of(spell.spell_id)).orElse(null);
        if (entry == null) return 0;
        // Effective range folds in caster modifiers; range.max is the action's engagement edge.
        return SpellHelper.getRange(this, entry) * spell.range.max;
    }

    /// ActiveTargetGoal whose detection range is the behaviour's `detection_range` rather
    /// than the GENERIC_FOLLOW_RANGE attribute. getFollowRange() drives both the candidate
    /// search box (horizontal extent) and the predicate's max-distance filter, and is also
    /// used by TrackTargetGoal.shouldContinue for retention — so overriding it scopes the
    /// whole acquire/keep lifecycle to detection_range. It reads detectionRange() from the
    /// outer entity (already populated before initGoals runs), so it is safe even when the
    /// superclass constructor calls getFollowRange() before this subclass is initialized.
    private class DetectionRangeTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {
        private DetectionRangeTargetGoal(Class<T> targetClass, int reciprocalChance,
                                         boolean checkVisibility, boolean checkCanNavigate,
                                         java.util.function.Predicate<LivingEntity> predicate) {
            super(SummonedEntity.this, targetClass, reciprocalChance, checkVisibility, checkCanNavigate, predicate);
        }

        @Override
        protected double getFollowRange() {
            return detectionRange();
        }
    }

    /// True if the entity currently has a live target. Used by passive navigation goals
    /// (wander, follow-summoner) to defer to combat behaviour the moment a target is
    /// acquired by any route — revenge, defend-owner, mirror-owner, or auto-aggro.
    private boolean hasLiveTarget() {
        LivingEntity target = getTarget();
        return target != null && target.isAlive();
    }

    // --- Target-clear policy ---
    //
    // Goal callbacks call onActionCompleted() (the on_action_completed list, each entry
    // rolling its own chance, first-match-wins). The per-tick loop in tick() calls
    // tickClearConditions() (after_ticks / out_of_detection_range, both deterministic).
    // Both share rollClearTarget() / setTarget(null) to drop the target.

    // Age at which the entity's current target was acquired. Reset by the setTarget
    // override below; `hasAcquiredTarget` guards the time-based check so stale state
    // (no target ever / target just cleared) doesn't fire it.
    private int targetAcquiredAtAge = 0;
    private boolean hasAcquiredTarget = false;

    // Owner-hit tracking for `attack_with_owner_hits`: counts consecutive hits the owner
    // lands on one target. Tracked on the entity (not inside MirrorOwnerAttackGoal) so the
    // count keeps building even while that goal is already committed to a different target,
    // letting the summon switch the moment it frees up. Reset whenever the owner's focus
    // changes; consumed when the goal commits.
    private int ownerLastAttackTimeSeen = 0;
    @Nullable private LivingEntity ownerHitTarget = null;
    private int ownerHitCount = 0;

    /// Observes the owner's attacks once per server tick and tallies consecutive hits on a
    /// single target. A new attack on the same target increments the count; switching to a
    /// different target restarts it at 1. `MirrorOwnerAttackGoal` reads the tally.
    private void tickOwnerAttackTracking() {
        if (behaviour == null || !behaviour.targeting.attack_with_owner) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;
        int attackTime = owner.getLastAttackTime();
        if (attackTime == ownerLastAttackTimeSeen) return; // no new attack since last tick
        ownerLastAttackTimeSeen = attackTime;
        LivingEntity attacked = owner.getAttacking();
        if (attacked == null) return;
        if (attacked == ownerHitTarget) {
            ownerHitCount++;
        } else {
            ownerHitTarget = attacked;
            ownerHitCount = 1;
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = getTarget();
        super.setTarget(target);
        if (target == null) {
            hasAcquiredTarget = false;
        } else if (target != previous) {
            targetAcquiredAtAge = age;
            hasAcquiredTarget = true;
        }
        // target == previous (non-null) → no-op: timer keeps counting from the
        // original acquisition, matching the "ticks held on this target" semantic.
    }

    /// Called by action goals once an action ran to completion (melee swing
    /// reached its full `duration`; spell cast reached release). Fires the
    /// `OnActionCompleted` trigger. `spellId` is only consulted for `SPELL_CAST`
    /// (callers should pass `null` for melee).
    public void onActionCompleted(SummonBehaviour.Action.Type actionType, @Nullable String spellId) {
        var clear = behaviour != null ? behaviour.targeting.clear_condition : null;
        if (clear == null) return;
        for (var t : clear.on_action_completed) {
            if (t.action_type != null && t.action_type != actionType) continue;
            // spell_id only narrows SPELL_CAST matches; for melee it's a no-op match.
            if (t.spell_id != null
                    && actionType == SummonBehaviour.Action.Type.SPELL_CAST
                    && !t.spell_id.equals(spellId)) continue;
            // First matching entry decides: roll its chance, then stop regardless of the
            // outcome — so a chance=0 entry acts as an exclusion ahead of broader entries.
            rollClearTarget(t.chance);
            return;
        }
    }

    /// Evaluates the per-tick clear triggers every server tick: `after_ticks` (held the
    /// target long enough) and `out_of_detection_range` (target fled beyond a multiple of
    /// the detection range). Both clear deterministically. No-ops while there is no target.
    /// Called from `tick()`.
    private void tickClearConditions() {
        if (!hasAcquiredTarget) return;
        var clear = behaviour != null ? behaviour.targeting.clear_condition : null;
        if (clear == null) return;
        var target = getTarget();
        if (target == null) return;
        if (clear.after_ticks != null && age - targetAcquiredAtAge >= clear.after_ticks.ticks) {
            setTarget(null);
            return;
        }
        if (clear.out_of_detection_range != null) {
            double threshold = clear.out_of_detection_range.multiplier * detectionRange();
            if (squaredDistanceTo(target) > threshold * threshold) {
                setTarget(null);
            }
        }
    }

    /// Rolls `chance` in `[0..1]` and nulls the target on success. <= 0 never clears;
    /// >= 1 always clears.
    private void rollClearTarget(float chance) {
        if (chance <= 0F) return;
        if (chance >= 1F || random.nextFloat() < chance) {
            setTarget(null);
        }
    }

    /// Snap yaw/pitch (and bodyYaw, so the model orients with the head) directly onto
    /// the target's eyes. Used during active engagement — spell casts and melee swings —
    /// to keep the entity locked on without the 30°/tick smoothing lag of `lookAt`.
    public void lockRotationTo(LivingEntity target) {
        Vec3d toTarget = target.getEyePos().subtract(getEyePos()).normalize();
        float yaw   = (float)  Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTarget.y))));
        setYaw(yaw);
        setHeadYaw(yaw);
        setBodyYaw(yaw);
        setPitch(pitch);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(PHASE, PHASE_SPAWNING);
        builder.add(COLLISION_MODE, (byte) SummonBehaviour.Movement.CollisionMode.ALL.ordinal());
        // 0 = sentinel for "no override". When the behaviour later sets a non-null
        // Dimensions, setBehaviour replaces these with the override values and
        // getBaseDimensions starts returning them; otherwise it falls through to
        // super.getBaseDimensions (the EntityType-declared size).
        builder.add(BOUNDING_BOX_WIDTH,  0F);
        builder.add(BOUNDING_BOX_HEIGHT, 0F);
        builder.add(END_OF_PHASE_AGE, 0);
        // duration = 0 → all action animations start inactive.
        long inactive = packAnim(0, 0, 0);
        builder.add(ATTACK_ANIMATION, inactive);
        builder.add(SPELL_CAST_ANIMATION, inactive);
        builder.add(SPELL_RELEASE_ANIMATION, inactive);
        builder.add(EXISTENCE_PARTICLES, "");
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        this.getDataTracker().set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.getDataTracker().get(OWNER_UUID).orElse(null);
    }

    @Nullable
    public LivingEntity getOwner() {
        UUID uuid = getOwnerUuid();
        if (uuid == null) return null;
        return this.getWorld().getPlayerByUuid(uuid);
    }

    // --- Animation states ---
    // Standard set shared by all summoned entities. Subclasses may override the hook methods
    // below if they need non-standard animation behaviour.

    public final AnimationState spawnAnimationState        = new AnimationState();
    public final AnimationState despawnAnimationState      = new AnimationState();
    public final AnimationState idleAnimationState         = new AnimationState();
    public final AnimationState moveAnimationState         = new AnimationState();
    public final AnimationState attackAnimationState       = new AnimationState();
    public final AnimationState spellCastAnimationState    = new AnimationState();
    public final AnimationState spellReleaseAnimationState = new AnimationState();

    /** Called each client tick. Default drives the five standard states from lifecycle phase. */
    protected void setupAnimationStates() {
        spawnAnimationState.setRunning(isSpawning(), this.age);
        boolean despawning = isDespawning();
        if (despawning && !despawnAnimationState.isRunning()) {
            // END_OF_PHASE_AGE = timeToLive during DESPAWNING (a future absolute age).
            // getTimeRunning() starts negative; with speedMultiplier=-1F it maps to
            // the end of the spawn animation and counts down to 0 as despawn progresses.
            despawnAnimationState.start(getDataTracker().get(END_OF_PHASE_AGE));
        } else if (!despawning) {
            despawnAnimationState.stop();
        }
        idleAnimationState.setRunning(isActive(), this.age);
        moveAnimationState.setRunning(isActive() && this.getVelocity().horizontalLength() > 0.01, this.age);

        // Auto-stop fixed-duration action animations once their duration has elapsed.
        autoStopActionAnimation(attackAnimationState,       ATTACK_ANIMATION);
        autoStopActionAnimation(spellCastAnimationState,    SPELL_CAST_ANIMATION);
        autoStopActionAnimation(spellReleaseAnimationState, SPELL_RELEASE_ANIMATION);
    }

    private void autoStopActionAnimation(AnimationState state, TrackedData<Long> descriptor) {
        if (!state.isRunning()) return;
        long d = getDataTracker().get(descriptor);
        int duration = animDuration(d);
        if (duration <= 0) {
            state.stop();
            return;
        }
        if (duration == DURATION_ENDLESS) return; // runs until an explicit stop arrives
        int startAge = animStartAge(d);
        if (age - startAge >= duration) state.stop();
    }

    /**
     * Called when a spell cast begins. The cast animation runs until `onSpellCastEnded()`
     * is invoked (no time-based auto-stop) — cast length isn't always known when the cast
     * starts, and short, premature stops felt choppy.
     */
    protected void onSpellCastStarted(int variant) {
        getDataTracker().set(SPELL_CAST_ANIMATION, packAnim(variant, DURATION_ENDLESS, age));
    }

    /** Stops the cast animation (e.g., on cancel or release). */
    protected void onSpellCastEnded() {
        // duration=0 = inactive; age in the payload guarantees a dirty write so the client
        // gets the stop transition even when the previous value was already "stopped".
        getDataTracker().set(SPELL_CAST_ANIMATION, packAnim(0, 0, age));
    }

    /** Called when a spell is released. Animation plays for `durationTicks`. */
    protected void onSpellReleased(int variant, int durationTicks) {
        getDataTracker().set(SPELL_RELEASE_ANIMATION, packAnim(variant, durationTicks, age));
    }

    /** Called when a melee swing begins. The animation plays for `durationTicks`. */
    protected void onAttackAnimated(int durationTicks, int variant) {
        getDataTracker().set(ATTACK_ANIMATION, packAnim(variant, durationTicks, age));
    }

    public int getAttackVariant()        { return animVariant(getDataTracker().get(ATTACK_ANIMATION)); }
    public int getSpellCastVariant()     { return animVariant(getDataTracker().get(SPELL_CAST_ANIMATION)); }
    public int getSpellReleaseVariant()  { return animVariant(getDataTracker().get(SPELL_RELEASE_ANIMATION)); }

    // Empty/null pool → variant 1 (the always-present default).
    public int pickVariant(@Nullable List<Integer> pool) {
        if (pool == null || pool.isEmpty()) return 1;
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Playback-speed multiplier the model should pass to `updateAnimation` for the swing,
     * so a keyframed animation of `animationLengthTicks` is compressed/stretched to fit the
     * current swing's configured duration.
     */
    public float getAttackAnimationSpeed(float animationLengthTicks) {
        int duration = animDuration(getDataTracker().get(ATTACK_ANIMATION));
        return duration > 0 ? animationLengthTicks / duration : 1F;
    }

    /** Same as `getAttackAnimationSpeed` but for the spell-release animation. */
    public float getSpellReleaseAnimationSpeed(float animationLengthTicks) {
        int duration = animDuration(getDataTracker().get(SPELL_RELEASE_ANIMATION));
        return duration > 0 ? animationLengthTicks / duration : 1F;
    }

    // --- Spell cooldowns ---

    private final SpellCooldownManager cooldownManager = new SpellCooldownManager(this);

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            setupAnimationStates();
            spawnExistenceParticles();
        } else {
            if (pendingSpawnSound) {
                pendingSpawnSound = false;
                if (behaviour != null) {
                    playConfiguredSound(behaviour.sounds.spawnEvent.get());
                    emitSpawnFx();
                }
            }
            cooldownManager.tickUpdate();
            if (timeToLive > 0 && this.age >= timeToLive) {
                this.discard();
            } else if (this.age < spawnEndAge) {
                setPhase(PHASE_SPAWNING);
            } else if (timeToLive > 0 && this.age >= despawnStartAge) {
                setPhase(PHASE_DESPAWNING);
            } else {
                setPhase(PHASE_ACTIVE);
            }
            // Tally the owner's hits so MirrorOwnerAttackGoal can apply attack_with_owner_hits.
            tickOwnerAttackTracking();
            // Per-tick clear triggers (after_ticks, out_of_detection_range) from clear_conditions.
            tickClearConditions();
            // Action animations are self-terminating now:
            //   - ATTACK_ANIMATION / SPELL_RELEASE_ANIMATION carry a fixed duration; the
            //     client stops their AnimationState once `age - startAge >= duration`.
            //   - SPELL_CAST_ANIMATION is ended explicitly by SpellCastGoal via
            //     onSpellCastEnded() on release or cancellation.
        }
    }

    // --- NBT ---

    private static final Gson GSON = new Gson();
    private static final String NBT_OWNER_UUID        = "OwnerUUID";
    private static final String NBT_TTL               = "TTL";
    private static final String NBT_SPAWN_END_AGE     = "SpawnEndAge";
    private static final String NBT_DESPAWN_START_AGE = "DespawnStartAge";
    private static final String NBT_BEHAVIOUR         = "Behaviour";

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid(NBT_OWNER_UUID)) {
            setOwnerUuid(nbt.getUuid(NBT_OWNER_UUID));
        }
        this.timeToLive      = nbt.getInt(NBT_TTL);
        this.spawnEndAge     = nbt.getInt(NBT_SPAWN_END_AGE);
        this.despawnStartAge = nbt.getInt(NBT_DESPAWN_START_AGE);
        if (nbt.contains(NBT_BEHAVIOUR)) {
            var behaviour = GSON.fromJson(nbt.getString(NBT_BEHAVIOUR), SummonBehaviour.class);
            setBehaviour(behaviour);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        UUID uuid = getOwnerUuid();
        if (uuid != null) {
            nbt.putUuid(NBT_OWNER_UUID, uuid);
        }
        nbt.putInt(NBT_TTL, this.timeToLive);
        nbt.putInt(NBT_SPAWN_END_AGE, this.spawnEndAge);
        nbt.putInt(NBT_DESPAWN_START_AGE, this.despawnStartAge);
        if (this.behaviour != null) {
            nbt.putString(NBT_BEHAVIOUR, GSON.toJson(this.behaviour));
        }
    }

    // --- Inner goal classes ---

    // Targets whoever attacked the owner (mirrors TrackOwnerAttackerGoal)
    private class DefendOwnerGoal extends TrackTargetGoal {
        private LivingEntity attacker;
        private int lastAttackedTime;

        public DefendOwnerGoal() {
            super(SummonedEntity.this, false);
            setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            LivingEntity owner = getOwner();
            if (owner == null) return false;
            attacker = owner.getAttacker();
            int time = owner.getLastAttackedTime();
            return time != lastAttackedTime
                    && canTrack(attacker, TargetPredicate.DEFAULT)
                    && canAttackTarget(attacker, owner);
        }

        @Override
        public void start() {
            SummonedEntity.this.setTarget(attacker);
            LivingEntity owner = getOwner();
            if (owner != null) lastAttackedTime = owner.getLastAttackedTime();
            super.start();
        }
    }

    // Joins the owner's current attack target (mirrors AttackWithOwnerGoal) once the owner
    // has hit it `attack_with_owner_hits` times. The hit tally lives on the entity
    // (ownerHitTarget / ownerHitCount, updated by tickOwnerAttackTracking).
    private class MirrorOwnerAttackGoal extends TrackTargetGoal {
        private LivingEntity attacking;

        public MirrorOwnerAttackGoal() {
            super(SummonedEntity.this, false);
            setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            LivingEntity owner = getOwner();
            if (owner == null) return false;
            int threshold = Math.max(1, behaviour != null ? behaviour.targeting.attack_with_owner_hits : 1);
            if (ownerHitTarget == null || ownerHitCount < threshold) return false;
            attacking = ownerHitTarget;
            return canTrack(attacking, TargetPredicate.DEFAULT)
                    && canAttackTarget(attacking, owner);
        }

        @Override
        public void start() {
            SummonedEntity.this.setTarget(attacking);
            // Consume the tally so the goal doesn't immediately re-fire on the same hits —
            // the owner must land another `threshold` hits to switch the summon again.
            ownerHitCount = 0;
            super.start();
        }
    }

    // Revenge that prefers the nearer threat. Unlike vanilla RevengeGoal — which always
    // switches to whoever landed the last hit — this only retargets when the new attacker
    // is closer than the current target, so the summon stays focused on whatever enemy is
    // in its face rather than chasing a distant sniper just because it scored a hit.
    private class ClosestAttackerRevengeGoal extends TrackTargetGoal {
        private int lastAttackedTime;

        public ClosestAttackerRevengeGoal() {
            super(SummonedEntity.this, true);
            setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            int time = getLastAttackedTime();
            if (time == lastAttackedTime) return false;
            LivingEntity attacker = getAttacker();
            if (attacker == null) return false;
            if (!canTrack(attacker, TargetPredicate.DEFAULT)) return false;
            LivingEntity currentTarget = getTarget();
            if (currentTarget != null && currentTarget.isAlive()
                    && squaredDistanceTo(attacker) >= squaredDistanceTo(currentTarget)) {
                // Attacker isn't closer — consume the hit so canStart doesn't re-fire on
                // every subsequent tick until the next attack lands.
                lastAttackedTime = time;
                return false;
            }
            return true;
        }

        @Override
        public void start() {
            setTarget(getAttacker());
            lastAttackedTime = getLastAttackedTime();
            super.start();
        }
    }

    // One-shot teleport to the owner when they are farther than `teleport_after_distance`.
    // Runs at a higher priority than action goals (priority 2 vs 10+) and claims Control.MOVE,
    // matching what SpellCastGoal / DynamicMeleeAttackGoal claim — so this preempts any
    // in-progress combat goal the moment it can start, rather than waiting for the summon to
    // disengage. The target is cleared after the teleport so the summon doesn't immediately
    // sprint back toward the same far-away enemy it was chasing.
    private class TeleportToSummonerGoal extends Goal {
        public TeleportToSummonerGoal() {
            setControls(EnumSet.of(Control.MOVE));
        }

        private SummonBehaviour.Movement.Follow follow() {
            return behaviour.movement.follow;
        }

        @Override
        public boolean canStart() {
            LivingEntity owner = getOwner();
            if (owner == null) return false;
            float teleportDist = follow().teleport_after_distance;
            if (teleportDist <= 0) return false;
            return squaredDistanceTo(owner) > teleportDist * teleportDist;
        }

        @Override
        public boolean shouldContinue() { return false; }

        @Override
        public void start() {
            LivingEntity owner = getOwner();
            if (owner == null) return;
            teleport(owner.getX(), owner.getY(), owner.getZ(), false);
            setTarget(null);
            getNavigation().stop();
        }
    }

    private class FollowSummonerGoal extends Goal {
        // Last horizontal velocity of the owner that was significant enough to determine orientation.
        // Null until the owner is seen moving; falls back to north when still null.
        @Nullable private Vec3d lastOwnerForward = null;

        public FollowSummonerGoal() {
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private SummonBehaviour.Movement.Follow follow() {
            return behaviour.movement.follow;
        }

        @Override
        public boolean canStart() {
            if (hasLiveTarget()) return false;
            LivingEntity owner = getOwner();
            if (owner == null) return false;
            float start = follow().start_distance;
            return squaredDistanceTo(owner) > start * start;
        }

        @Override
        public boolean shouldContinue() {
            if (hasLiveTarget()) return false;
            LivingEntity owner = getOwner();
            if (owner == null) return false;
            float stop = follow().stop_distance;
            return squaredDistanceTo(owner) > stop * stop;
        }

        @Override
        public void start() {
            LivingEntity owner = getOwner();
            if (owner == null) return;
            Vec3d target = computeFollowTarget(owner);
            getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
        }

        @Override
        public void tick() {
            LivingEntity owner = getOwner();
            if (owner == null) return;
            Vec3d target = computeFollowTarget(owner);
            getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
        }

        // Returns a position 2 blocks to the owner's right side.
        // Forward is taken from the owner's current velocity when significant; otherwise the last
        // cached forward is reused. If no valid forward has ever been observed, falls back to north.
        private Vec3d computeFollowTarget(LivingEntity owner) {
            Vec3d vel = owner.getVelocity();
            double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (horizSpeed > 0.02) {
                lastOwnerForward = new Vec3d(vel.x / horizSpeed, 0, vel.z / horizSpeed);
            }
            // North (-Z) as the last-resort default before the owner has ever moved
            Vec3d forward = lastOwnerForward != null ? lastOwnerForward : new Vec3d(0, 0, -1);
            // Right = forward rotated 90° clockwise (viewed from above) in Minecraft's coordinate system
            Vec3d right = new Vec3d(-forward.z, 0, forward.x);
            return owner.getPos().add(right.multiply(2.0));
        }
    }

    // Vanilla wander, gated on target presence: the summon stops drifting the moment a
    // target is acquired by any route, and won't restart wandering until the target is gone.
    private class WanderWhenIdleGoal extends WanderAroundFarGoal {
        public WanderWhenIdleGoal(double speed, float probability) {
            super(SummonedEntity.this, speed, probability);
        }

        @Override
        public boolean canStart() {
            if (hasLiveTarget()) return false;
            return super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            if (hasLiveTarget()) return false;
            return super.shouldContinue();
        }
    }

    // Holds MOVE/LOOK/JUMP controls during spawn and despawn phases, making the entity inactionable.
    private class PhaseBlockGoal extends Goal {
        public PhaseBlockGoal() {
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() { return !isActive(); }

        @Override
        public boolean shouldContinue() { return !isActive(); }
    }

    // Holds LOOK control whenever the entity has an attack target, keeping it facing that target
    // between spell casts and melee attacks. Sits just below action goals so it is displaced
    // when any combat goal is active but takes over as soon as they release controls.
    private class FaceTargetGoal extends Goal {
        public FaceTargetGoal() {
            setControls(EnumSet.of(Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && isActive();
        }

        @Override
        public boolean shouldContinue() { return canStart(); }

        @Override
        public boolean shouldRunEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().lookAt(target, 30F, 30F);
            setBodyYaw(getHeadYaw());
        }
    }

    private class SpellCastGoal extends Goal {
        private final SummonBehaviour.Action.SpellCast config;

        // Spell registry entry — resolved lazily and cached (registry is stable at runtime)
        @Nullable private RegistryEntry<Spell> spellEntry = null;
        private boolean spellLookupAttempted = false;

        // Per-activation state
        private int castTick = 0;
        private int castDuration = 1;
        private boolean released = false;
        // How this activation aims, resolved once when the cast begins: a tracked target or
        // a stationary fallback (forward / self). Holds all per-activation aiming state, so
        // the lifecycle methods never branch on the configured mode. Capturing it at start()
        // also pins the target for the whole cast, so a mid-cast RevengeGoal retarget can't
        // redirect the spell at a different enemy. Cleared in stop().
        @Nullable private CastAim aim = null;

        public SpellCastGoal(SummonBehaviour.Action.SpellCast config) {
            this.config = config;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Nullable
        private RegistryEntry<Spell> resolveSpell() {
            if (spellLookupAttempted) return spellEntry;
            spellLookupAttempted = true;
            var id = Identifier.of(config.spell_id);
            spellEntry = SpellRegistry.from(getWorld()).getEntry(id).orElse(null);
            return spellEntry;
        }

        // Resolved engagement distances. The `range.{min,max,preferred}` fields are
        // FRACTIONS of the spell's effective range (i.e. `SpellHelper.getRange`, which
        // applies caster-level modifiers like gear and attributes — not the raw
        // `spell.range`), so they auto-scale per caster.
        private float spellRange(RegistryEntry<Spell> entry) {
            return SpellHelper.getRange(SummonedEntity.this, entry);
        }
        private float effectiveMin(RegistryEntry<Spell> entry) {
            return config.range.min * spellRange(entry);
        }
        private float effectiveMax(RegistryEntry<Spell> entry) {
            return config.range.max * spellRange(entry);
        }
        private float effectivePreferred(RegistryEntry<Spell> entry) {
            return config.range.preferred * spellRange(entry);
        }

        @Override
        public boolean shouldRunEveryTick() { return true; }

        @Override
        public boolean canStart() {
            var entry = resolveSpell();
            if (entry == null) return false;
            var spell = entry.value();
            if (spell.active == null) return false;           // ACTIVE spells only
            if (SpellHelper.isChanneled(spell)) return false; // INSTANT or CHARGE only
            if (!isActive()) return false;                    // not in spawn/despawn phase
            if (cooldownManager.isCoolingDown(entry)) return false;
            return resolveAim(entry) != null;                 // is there anything to fire at?
        }

        @Override
        public void start() {
            castTick = 0;
            released = false;
            var entry = spellEntry; // already resolved by canStart()
            aim = resolveAim(entry);
            if (entry != null) {
                var spell = entry.value();
                castDuration = SpellHelper.isInstant(spell)
                        ? 1
                        : SpellHelper.getCastTimeDetails(SummonedEntity.this, spell).length();
                if (castDuration <= 0) castDuration = 1;
            }
            onSpellCastStarted(pickVariant(config.cast_animation_variants));
            setAttacking(true);
        }

        @Override
        public boolean shouldContinue() {
            return !released && isActive() && aim != null && aim.valid();
        }

        @Override
        public void stop() {
            setAttacking(false);
            getNavigation().stop();
            aim = null;
            // Covers both the natural-end path (release ran, goal then stopped next tick)
            // and early cancellation (target lost mid-cast, etc.). Idempotent.
            onSpellCastEnded();
            // Only consult clear_conditions when the spell actually fired this activation —
            // cancellations (target lost, LOS broken, etc.) are not "the action completed".
            if (released) {
                onActionCompleted(SummonBehaviour.Action.Type.SPELL_CAST, config.spell_id);
            }
        }

        @Override
        public void tick() {
            if (released || aim == null) return;
            var entry = spellEntry;
            if (entry == null) return;
            aim.approach();                 // chase the subject (no-op when stationary)
            aim.orient();                   // face the way the spell must fire
            if (aim.engaged()) castTick++;  // progress only while in range and visible
            if (castTick >= castDuration) {
                releaseSpell(entry, entry.value());
                released = true;
            }
        }

        private void releaseSpell(RegistryEntry<Spell> entry, Spell spell) {
            if (getWorld().isClient()) return;
            // Final orient before the engine reads the look vector: targetAndPerformSpell
            // raycasts (AIM/BEAM) and launches projectiles along the caster's facing.
            aim.orient();
            SpellHelper.targetAndPerformSpell(getWorld(), SummonedEntity.this, entry);
            onSpellCastEnded();
            onSpellReleased(pickVariant(config.release_animation_variants), config.release_animation_duration);

            // Cooldown: use spell's own duration if set, else fall back to config override (ticks)
            int cooldownTicks;
            if (spell.cost.cooldown != null && spell.cost.cooldown.duration > 0) {
                cooldownTicks = Math.round(
                        SpellHelper.getCooldownDuration(SummonedEntity.this, entry) * 20F);
            } else {
                cooldownTicks = config.cooldown; // already in ticks; default = 20
            }
            if (cooldownTicks > 0) {
                cooldownManager.set(entry, cooldownTicks);
            }
        }

        // --- Aim resolution ---

        /// Picks how this activation aims: the live target when targeting is enabled and a
        /// usable target sits inside the engagement band, otherwise the configured fallback.
        /// Returns null when there is nothing to fire at (fallback NONE with no target), so
        /// the goal simply doesn't start.
        @Nullable
        private CastAim resolveAim(RegistryEntry<Spell> entry) {
            if (config.aiming.accept_target && entry != null) {
                var target = getTarget();
                if (target != null && target.isAlive() && withinBand(entry, target)) {
                    return new TrackedAim(target);
                }
            }
            return switch (config.aiming.fallback) {
                case NONE    -> null;
                case FORWARD -> forwardAim;
                case SELF    -> selfAim;
            };
        }

        /// True when the target sits inside `[min, max] × effective range`. Defaults
        /// (`min = 0`, `max = 1`) mean "anywhere within the spell's effective range".
        private boolean withinBand(RegistryEntry<Spell> entry, LivingEntity target) {
            double distSq = squaredDistanceTo(target);
            float maxR = effectiveMax(entry);
            if (distSq > (double) maxR * maxR) return false;
            float minR = effectiveMin(entry);
            return minR <= 0 || distSq >= (double) minR * minR;
        }

        /// Per-activation aiming strategy. Each method answers one question the cast
        /// lifecycle asks, so the lifecycle code stays free of mode checks.
        private interface CastAim {
            /// May the goal keep running? (subject still valid, or always for stationary)
            boolean valid();
            /// Move toward the subject. No-op for stationary fallbacks.
            void approach();
            /// May the cast counter advance this tick? (in range and visible, or always)
            boolean engaged();
            /// Rotate the entity so the spell fires the right way.
            void orient();
        }

        /// Tracks the entity's current target: navigates to `preferred × range`, follows
        /// line-of-sight, and locks rotation onto it. The target is captured for the whole
        /// cast, so a mid-cast retarget can't redirect the spell.
        private class TrackedAim implements CastAim {
            private final LivingEntity target;
            private int seeingTicker = 0;
            private boolean inPreferredRange = false;

            private TrackedAim(LivingEntity target) { this.target = target; }

            @Override
            public boolean valid() {
                if (!target.isAlive()) return false;
                // Enforce the upper engagement edge mid-cast (a target leaving `max` aborts).
                // The lower edge is intentionally not re-checked, so a target stepping inside
                // `min` mid-cast still gets hit rather than cancelling the goal.
                float maxR = effectiveMax(spellEntry);
                return squaredDistanceTo(target) <= (double) maxR * maxR;
            }

            @Override
            public void approach() {
                float preferred = effectivePreferred(spellEntry);
                double preferredSq = (double) preferred * preferred;
                inPreferredRange = squaredDistanceTo(target) <= preferredSq;
                boolean canSee = getVisibilityCache().canSee(target);
                if (canSee) { if (seeingTicker < 10) seeingTicker++; }
                else        { if (seeingTicker > 0)  seeingTicker--; }
                // Close in until inside preferred range (or while sight is broken); then hold.
                // No retreat — a target stepping closer just gets cast on from where we stand.
                if (!inPreferredRange || seeingTicker <= 0) {
                    getNavigation().startMovingTo(target, 1.0);
                } else {
                    getNavigation().stop();
                }
            }

            @Override
            public boolean engaged() { return inPreferredRange && seeingTicker > 0; }

            @Override
            public void orient() { lockRotationTo(target); }
        }

        /// Shared base for stationary fallbacks: never chases, fires on cooldown, runs to
        /// release. Subclasses only choose where to point.
        private abstract class StationaryAim implements CastAim {
            @Override public boolean valid()   { return true; }
            @Override public void approach()   { }
            @Override public boolean engaged() { return true; }
        }

        /// Fires straight ahead along the entity's current (spawn-set) facing — turret.
        private final CastAim forwardAim = new StationaryAim() {
            @Override public void orient() { /* keep current facing */ }
        };

        /// Aims at the entity's own position (pitch straight down), so directional spells
        /// resolve at its feet; self-centred AoE spells are unaffected by rotation.
        private final CastAim selfAim = new StationaryAim() {
            @Override public void orient() {
                setPitch(90F);
                setHeadYaw(getYaw());
                setBodyYaw(getYaw());
            }
        };
    }
}
