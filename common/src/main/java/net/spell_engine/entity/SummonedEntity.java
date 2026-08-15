package net.spell_engine.entity;

import com.google.common.base.Suppliers;
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
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
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
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.spell_engine.api.entity.TwoWayCollisionChecker;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.summon.AttributeScaling;
import net.spell_engine.api.spell.summon.SpellSummoned;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.api.spell.summon.SummonedEntityConfig;
import net.spell_engine.entity.goal.*;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.target.EntityRelation;
import net.spell_engine.internals.target.EntityRelations;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.spell_engine.internals.SpellParameters;

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

    // Client-visible mirror of `behaviour.is_attackable`. The `behaviour` field is server-only (set at
    // spawn / on NBT load, never synced), so the combat/targeting getters below can't read it on the
    // client. Without this, client-side `canHit()` falls through to `super.canHit()` = true, and the
    // client's own spell target search (TargetHelper.targetsFromArea/…, which filters on canHit) would
    // highlight and select non-attackable summons — e.g. an AoE heal targeting a Lightwell. Synced here
    // so both sides agree. Defaults true (matches the historical `behaviour == null` fallback).
    public static final TrackedData<Boolean> IS_ATTACKABLE =
            DataTracker.registerData(SummonedEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

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
    @Nullable public SummonBehaviour behaviour = null;
    // Owner-scaled attribute bonuses, applied once at spawn (and re-applied on reload). Kept separate
    // from `behaviour` and persisted on its own, because the scaling is a spawn-time effect rather
    // than part of the runtime behaviour, and the resulting attribute modifiers are temporary.
    @Nullable public AttributeScaling attributeScaling = null;

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

    /// Whether this summon may be attacked/targeted. Reads the DataTracker mirror (see {@link #IS_ATTACKABLE})
    /// rather than the server-only `behaviour` field, so it is correct on the client too — the client's
    /// spell target search filters on `canHit()`, and a stale client-side `true` would wrongly select
    /// non-attackable summons. Defaults true before `setBehaviour` runs.
    public boolean isAttackableSummon() {
        return getDataTracker().get(IS_ATTACKABLE);
    }

    @Override
    public boolean isAttackable() {
        return isAttackableSummon() && super.isAttackable();
    }

    @Override
    public boolean canHit() {
        return isAttackableSummon() && super.canHit();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return isAttackableSummon() && super.damage(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return !isAttackableSummon() && super.isInvulnerableTo(damageSource);
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
        if (!isAttackableSummon()) return true;
        return super.isInvulnerable();
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return !isAttackableSummon() && super.isImmuneToExplosion(explosion);
    }

    public void takeKnockback(double strength, double x, double z) {
        if (!isAttackableSummon()) {
            return;
        }
        super.takeKnockback(strength, x, z);
    }

    /// Gates vanilla suffocation on the behaviour's `movement.suffocates` (default false = immune).
    /// `isInsideWall()` has exactly one consumer in vanilla — the `inWall` damage in
    /// `LivingEntity.baseTick` — so overriding it is a targeted opt-out with no other side effects.
    ///
    /// Worth knowing when setting `suffocates`: vanilla's check samples a thin slab at EYE level only
    /// (eye height = `height * 0.85`), so a summon whose behaviour overrides `dimensions` to be taller
    /// than the space it was placed in suffocates even with its feet in the clear.
    ///
    /// Reading the server-only `behaviour` field is safe here (unlike the combat getters above, which
    /// need the DataTracker mirror): vanilla guards the call with `!world.isClient`, and both
    /// `onSummonedBySpell` and `readCustomDataFromNbt` install the behaviour before the first tick.
    /// A null behaviour falls through to immune, matching the field's default.
    @Override
    public boolean isInsideWall() {
        return behaviour != null && behaviour.movement.suffocates && super.isInsideWall();
    }

    // --- Sounds ---
    // Every configured summon sound is now an fx.Sound (id + volume + pitch + randomness); the id is
    // resolved to a registered SoundEvent lazily and memoized here, on the entity rather than on the
    // SummonBehaviour — a Gson-deserialized behaviour (rebuilt from NBT/JSON) cannot be relied upon to
    // carry live `transient Supplier` fields (allocation paths that bypass field initializers leave
    // them null, which NPE'd on first playback). Each lambda reads `behaviour` lazily; call sites only
    // invoke `.get()` after guarding `behaviour != null`, so the memoized value is always resolved from
    // an installed behaviour. Only hurt/death/ambient need memoized events (returned to vanilla's
    // getXxxSound and used to re-identify the vanilla-emitted event in playSound); spawn/despawn/swing/
    // impact/step are emitted directly from their fx.Sound at the call site.
    private final Supplier<SoundEvent> hurtEvent    = Suppliers.memoize(() -> resolveEvent(behaviour != null ? behaviour.sounds.hurt    : null));
    private final Supplier<SoundEvent> deathEvent   = Suppliers.memoize(() -> resolveEvent(behaviour != null ? behaviour.sounds.death   : null));
    private final Supplier<SoundEvent> ambientEvent = Suppliers.memoize(() -> resolveEvent(behaviour != null ? behaviour.sounds.ambient : null));

    /// Resolves an fx.Sound's id into its **registered** SoundEvent instance. Null Sound / null-or-blank
    /// id / unparseable / unregistered → null.
    ///
    /// Must return the instance actually held in `Registries.SOUND_EVENT` (via `get`), NOT a freshly
    /// fabricated `SoundEvent.of(id)`. Server-side playback (`World.playSound(SoundEvent)`) converts
    /// the event back to a `RegistryEntry` through `Registries.SOUND_EVENT.getEntry(value)`, which
    /// looks the value up in an *identity* map — a fabricated instance is never found, yielding a null
    /// RegistryEntry that NPEs in `ServerWorld.playSound`. The registered instance resolves correctly.
    @Nullable
    private static SoundEvent resolveEvent(@Nullable Sound sound) {
        if (sound == null || sound.id() == null || sound.id().isBlank()) return null;
        Identifier id = Identifier.tryParse(sound.id());
        return id != null ? Registries.SOUND_EVENT.get(id) : null;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        if (behaviour != null) {
            SoundEvent custom = hurtEvent.get();
            if (custom != null) return custom;
        }
        return super.getHurtSound(source);
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        if (behaviour != null) {
            SoundEvent custom = deathEvent.get();
            if (custom != null) return custom;
        }
        return super.getDeathSound();
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        if (behaviour != null) {
            SoundEvent custom = ambientEvent.get();
            if (custom != null) return custom;
        }
        return super.getAmbientSound();
    }

    // Vanilla emits the hurt and death sounds through the single-arg `playSound(SoundEvent)` (from
    // `playHurtSound` and inline in `damage()`), which plays at `getSoundVolume()` — ignoring the
    // fx.Sound's own volume/pitch. Intercept here: when the event is the one our configured hurt/death
    // Sound resolves to, re-emit it honoring that Sound's volume/pitch/randomness. All other vanilla
    // single-arg playSound calls pass straight through.
    @Override
    public void playSound(@Nullable SoundEvent sound) {
        if (sound != null && behaviour != null) {
            if (sound == hurtEvent.get())  { playConfiguredSound(behaviour.sounds.hurt);  return; }
            if (sound == deathEvent.get()) { playConfiguredSound(behaviour.sounds.death); return; }
        }
        super.playSound(sound);
    }

    // Vanilla MobEntity.playAmbientSound() is `this.playSound(this.getAmbientSound())` with NO null
    // guard — a null return reaches ServerWorld.playSound and NPEs on `sound.value()`. Summons treat
    // the ambient sound as optional (null / unregistered → no sound), so guard it here and honor the
    // configured fx.Sound's volume/pitch.
    @Override
    public void playAmbientSound() {
        if (behaviour != null && resolveEvent(behaviour.sounds.ambient) != null) {
            playConfiguredSound(behaviour.sounds.ambient);
            return;
        }
        SoundEvent sound = super.getAmbientSound();
        if (sound != null) {
            playSound(sound, getSoundVolume(), getSoundPitch());
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (behaviour != null) {
            Sound step = behaviour.sounds.step;
            SoundEvent custom = resolveEvent(step);
            if (custom != null) {
                // Mirrors the vanilla footstep volume scaling (15% of normal) so the sound doesn't
                // dominate while the entity walks, scaled further by the configured Sound's volume.
                this.playSound(custom, 0.15F * step.volume(), step.randomizedPitch());
                return;
            }
        }
        super.playStepSound(pos, state);
    }

    /// Broadcasts a configured fx.Sound from this entity's position, honoring its volume, pitch and
    /// pitch-randomness. Silent when `sound` is null or its id is unset/unregistered.
    public void playConfiguredSound(@Nullable Sound sound) {
        SoundEvent event = resolveEvent(sound);
        if (event == null) return;
        getWorld().playSound(null, getX(), getY(), getZ(),
                event, getSoundCategory(), sound.volume(), sound.randomizedPitch());
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
        var fx = behaviour.spawn_fx.resolved(Fx.Context.NONE);
        var world = getWorld();
        if (!fx.particles.isEmpty()) {
            ParticleHelper.sendBatches(this, fx.particles);
        }
        ModelEffectHelper.spawn(world, getPos(), getYaw(), fx.models, this);
    }

    /// Server-side: emits the individual despawn FX once, when the entity enters its despawn phase.
    /// Mirrors {@link #emitSpawnFx()}: particles go out as a tracker packet, model effects as
    /// self-syncing entities, both at this entity.
    private void emitDespawnFx() {
        if (behaviour == null || behaviour.despawn_fx == null) return;
        var fx = behaviour.despawn_fx.resolved(Fx.Context.NONE);
        var world = getWorld();
        if (!fx.particles.isEmpty()) {
            ParticleHelper.sendBatches(this, fx.particles);
        }
        ModelEffectHelper.spawn(world, getPos(), getYaw(), fx.models, this);
    }

    /// Client-side: spawns the configured existence particles locally on their interval, during the
    /// ACTIVE phase. No network traffic — the config was synced once via EXISTENCE_PARTICLES.
    private void spawnExistenceParticles() {
        if (clientExistenceParticles == null || !isActive()) return;
        var world = getWorld();
        for (var ep : clientExistenceParticles) {
            if (ep == null || ep.particles == null || ep.particles.isEmpty() || ep.interval_ticks <= 0) {
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
            playConfiguredSound(behaviour.sounds.despawn);
            emitDespawnFx();
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
        this.attributeScaling = args.attribute_scaling; // set before setBehaviour applies it
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
        getDataTracker().set(IS_ATTACKABLE, behaviour.is_attackable);
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

    /// Builds the default attribute container for a summoned entity type from its config entry: the four
    /// common attributes plus any custom (e.g. spell-power school) attributes. Shared by all summon entity
    /// types — registered via `SummonedEntities.registerAttributes` rather than a per-entity method.
    public static DefaultAttributeContainer.Builder createAttributes(SummonedEntityConfig.Entry entry) {
        var builder = LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, entry.common.follow_range)
                .add(EntityAttributes.GENERIC_MAX_HEALTH, entry.common.max_health)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, entry.common.movement_speed)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, entry.common.attack_damage);
        for (var custom : entry.custom) {
            Registries.ATTRIBUTE.getEntry(Identifier.of(custom.id)).ifPresent(e -> builder.add(e, custom.value));
        }
        return builder;
    }

    private void applyAttributeScaling(LivingEntity owner) {
        if (attributeScaling == null) return;
        var healthRatio = this.getHealth() / this.getMaxHealth();
        for (var entry : attributeScaling.entries) {
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

        // SwimGoal keeps the entity afloat by bobbing it to the surface (and flips the navigation
        // to swim-capable). A summon that cannot move must stay put — including in water — so only
        // install it when movement is enabled. Otherwise a stationary summon would drift upward.
        if (behaviour.movement.can_move) {
            goalSelector.add(0, new SwimGoal(this));
        }
        goalSelector.add(1, new PhaseBlockGoal(this));
        // Teleport is intentionally ordered above action goals so it preempts an in-progress
        // melee swing or spell cast. Walk-follow stays below them (added later, priority
        // after FaceTargetGoal) so normal catch-up doesn't interrupt active combat.
        if (behaviour.movement.can_move && behaviour.movement.follow != null) {
            goalSelector.add(2, new TeleportToSummonerGoal(this));
        }
        int actionPriority = 10;
        for (var action : behaviour.actions) {
            switch (action.type) {
                case MELEE_ATTACK -> goalSelector.add(actionPriority, new DynamicMeleeAttackGoal(this, action.melee_attack));
                case SPELL_CAST -> goalSelector.add(actionPriority, new SpellCastGoal(this, action.spell_cast));
            }
            actionPriority++;
        }
        int priority = actionPriority;
        goalSelector.add(priority++, new FaceTargetGoal(this));
        var movement = behaviour.movement;
        if (movement.can_move) {
            if (movement.follow != null) {
                goalSelector.add(priority++, new FollowSummonerGoal(this));
            }
            goalSelector.add(priority++, new WanderWhenIdleGoal(this, movement.wander.speed, movement.wander.probability));
        }
        if (behaviour.targeting.look_around) {
            goalSelector.add(priority++, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
            goalSelector.add(priority, new LookAroundGoal(this));
        }

        // --- Target selector ---

        if (behaviour.targeting.attack_with_owner) {
            targetSelector.add(1, new DefendOwnerGoal(this));
            targetSelector.add(3, new MirrorOwnerAttackGoal(this));
        }
        if (behaviour.targeting.revenge) {
            targetSelector.add(2, new ClosestAttackerRevengeGoal(this));
        }
        // Friendly goal added first (lower priority number) so wounded-ally healing takes
        // precedence over hostile acquisition when BOTH is configured.
        switch (behaviour.targeting.automatic_targeting) {
            case FRIENDLY -> targetSelector.add(4, new DetectionRangeTargetGoal<>(this, LivingEntity.class, 10, true, false, this::shouldHealTarget));
            case HOSTILE  -> targetSelector.add(4, new DetectionRangeTargetGoal<>(this, MobEntity.class,    10, true, false, this::shouldTarget));
            case BOTH     -> {
                targetSelector.add(4, new DetectionRangeTargetGoal<>(this, LivingEntity.class, 10, true, false, this::shouldHealTarget));
                targetSelector.add(5, new DetectionRangeTargetGoal<>(this, MobEntity.class,    10, true, false, this::shouldTarget));
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
    public double detectionRange() {
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
    /// (`SpellParameters.getRange` × the action's `range.max` fraction) and melee reach
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
        return SpellParameters.getRange(this, entry) * spell.range.max;
    }

    /// True if the entity currently has a live target. Used by passive navigation goals
    /// (wander, follow-summoner) to defer to combat behaviour the moment a target is
    /// acquired by any route — revenge, defend-owner, mirror-owner, or auto-aggro.
    public boolean hasLiveTarget() {
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

    /// The target the owner has been hitting (see `tickOwnerAttackTracking`). Read by MirrorOwnerAttackGoal.
    @Nullable public LivingEntity getOwnerHitTarget() { return ownerHitTarget; }
    /// Consecutive hits the owner has landed on `getOwnerHitTarget()`.
    public int getOwnerHitCount() { return ownerHitCount; }
    /// Resets the owner-hit tally; called once MirrorOwnerAttackGoal commits to the target.
    public void consumeOwnerHits() { ownerHitCount = 0; }

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
        builder.add(IS_ATTACKABLE, true);
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
    public void onSpellCastStarted(int variant) {
        getDataTracker().set(SPELL_CAST_ANIMATION, packAnim(variant, DURATION_ENDLESS, age));
    }

    /** Stops the cast animation (e.g., on cancel or release). */
    public void onSpellCastEnded() {
        // duration=0 = inactive; age in the payload guarantees a dirty write so the client
        // gets the stop transition even when the previous value was already "stopped".
        getDataTracker().set(SPELL_CAST_ANIMATION, packAnim(0, 0, age));
    }

    /** Called when a spell is released. Animation plays for `durationTicks`. */
    public void onSpellReleased(int variant, int durationTicks) {
        getDataTracker().set(SPELL_RELEASE_ANIMATION, packAnim(variant, durationTicks, age));
    }

    /** Called when a melee swing begins. The animation plays for `durationTicks`. */
    public void onAttackAnimated(int durationTicks, int variant) {
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

    public final SpellCooldownManager cooldownManager = new SpellCooldownManager(this);

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
                    playConfiguredSound(behaviour.sounds.spawn);
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
    private static final String NBT_ATTRIBUTE_SCALING = "AttributeScaling";

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid(NBT_OWNER_UUID)) {
            setOwnerUuid(nbt.getUuid(NBT_OWNER_UUID));
        }
        this.timeToLive      = nbt.getInt(NBT_TTL);
        this.spawnEndAge     = nbt.getInt(NBT_SPAWN_END_AGE);
        this.despawnStartAge = nbt.getInt(NBT_DESPAWN_START_AGE);
        // Read before setBehaviour, which (re-)applies the scaling.
        if (nbt.contains(NBT_ATTRIBUTE_SCALING)) {
            this.attributeScaling = GSON.fromJson(nbt.getString(NBT_ATTRIBUTE_SCALING), AttributeScaling.class);
        }
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
        if (this.attributeScaling != null) {
            nbt.putString(NBT_ATTRIBUTE_SCALING, GSON.toJson(this.attributeScaling));
        }
    }

}
