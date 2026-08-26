package net.spell_engine.entity;

import net.minecraft.entity.AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.SoundPlayerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;

public class SpellCloud extends Entity implements Ownable {
    public static EntityType<SpellCloud> ENTITY_TYPE;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID ownerUuid;
    private int timeToLive;
    // Lifecycle phase durations (ticks), mirroring SummonedEntity. Stored as durations rather than
    // absolute age points so they survive an NBT round-trip regardless of the entity's age:
    //   [0, spawnDuration)                   -> SPAWNING   (warm-up, no impacts)
    //   [spawnDuration, ttl - despawnDur)    -> ACTIVE     (impacts performed)
    //   [ttl - despawnDuration, timeToLive)  -> DESPAWNING (wind-down, no impacts)
    private int spawnDuration = 0;
    private int despawnDuration = 0;
    private int impactsPerformed = 0;
    private int impactCap = 0;
    private Identifier spellId;
    private int dataIndex = 0;
    private SpellExecution.ImpactContext context;
    /// Cloud-targeting spell modifiers (radius / growth) summed at spawn. Persisted as one small compound
    /// so a mid-life reload keeps the bonus — the caster's modifiers aren't re-derivable from the entity.
    private Spell.Modifier.Cloud cloudModifier = new Spell.Modifier.Cloud();

    // MARK: Lifecycle phases

    public static final byte PHASE_SPAWNING   = 0;
    public static final byte PHASE_ACTIVE     = 1;
    public static final byte PHASE_DESPAWNING = 2;

    public SpellCloud(EntityType<? extends SpellCloud> entityType, World world) {
        super(entityType, world);
        // Eagerly start the spawn animation so the first render frame already has its t=0 keyframes
        // (e.g. scale 0), avoiding a one-frame flash at full size before setupAnimationStates() runs.
        // Harmless when there's no spawning phase: the first client tick stops it via setRunning(false).
        spawnAnimationState.startIfNotRunning(0);
    }

    public SpellCloud(World world) {
        super(ENTITY_TYPE, world);
        this.noClip = true;
    }

    public void onCreatedFromSpell(Identifier spellId, Spell.Delivery.Cloud cloudData, SpellExecution.ImpactContext context, float time_to_live_seconds, Spell.Modifier.Cloud cloudModifier) {
        this.spellId = spellId;
        this.context = context;
        this.cloudModifier = cloudModifier;

        var spellEntry = getSpellEntry();
        if (spellEntry != null) {
            var spell = spellEntry.value();
            var index = 0;
            var dataList = spell.deliver.clouds;
            if (!dataList.isEmpty()) {
                index = dataList.indexOf(cloudData);
            }
            this.dataIndex = index;
        }
        this.getDataTracker().set(SPELL_ID_TRACKER, this.spellId.toString());
        this.getDataTracker().set(DATA_INDEX_TRACKER, this.dataIndex);
        this.getDataTracker().set(RADIUS_TRACKER, radiusForAge(this.age));

        // Carve the lifetime into spawning / active / despawning, like a summoned entity.
        // `time_to_live_seconds` is the ACTIVE duration; spawn/despawn bracket it.
        this.spawnDuration   = cloudData.spawn_ticks;
        this.despawnDuration = cloudData.despawn_ticks;
        int activeTicks      = (int) (time_to_live_seconds * 20);
        this.timeToLive      = this.spawnDuration + activeTicks + this.despawnDuration;
        this.impactCap = cloudData.impact_cap;
    }

    // MARK: Phase accessors

    public boolean isSpawning()   { return getDataTracker().get(PHASE_TRACKER) == PHASE_SPAWNING; }
    public boolean isActive()     { return getDataTracker().get(PHASE_TRACKER) == PHASE_ACTIVE; }
    public boolean isDespawning() { return getDataTracker().get(PHASE_TRACKER) == PHASE_DESPAWNING; }

    /// Server-side: cut the remaining lifetime short and enter DESPAWNING for `ticks`, after which
    /// `tick()` discards this entity through the normal `age >= timeToLive` path.
    ///
    /// Encoding the wind-down in `timeToLive`/`despawnDuration` — rather than tracking a separate
    /// "ending" flag — keeps the age-derived phase in `tick()` self-consistent (`age >= timeToLive -
    /// despawnDuration` is true from this tick onward) and, since both fields are persisted, survives
    /// an NBT round-trip.
    ///
    /// `ticks <= 0` discards immediately. Already despawning is a no-op, so the first caller wins:
    /// a subclass may pick its own wind-down length from `onImpactPerformed` before the impact-cap
    /// check in `tick()` falls back to the cloud's configured `despawn_ticks`.
    protected void beginDespawn(int ticks) {
        if (getEntityWorld().isClient() || isDespawning()) {
            return;
        }
        if (ticks <= 0) {
            this.discard();
            return;
        }
        this.despawnDuration = ticks;
        this.timeToLive = this.age + ticks;
        // Publish now; waiting for the next tick would leak one frame of the ACTIVE pose.
        setPhase(PHASE_DESPAWNING);
    }

    /// Server-side: publishes the current phase and the absolute age at which it ends, so the client
    /// can drive the spawn/despawn scale without knowing the raw boundary fields (mirrors SummonedEntity).
    private void setPhase(byte phase) {
        if (getDataTracker().get(PHASE_TRACKER) != phase) {
            getDataTracker().set(PHASE_TRACKER, phase);
            // Fire the wind-down FX exactly on the ACTIVE -> DESPAWNING transition. This is the sole
            // server-side choke point for phase changes (both the age-based expiry in `tick()` and
            // `beginDespawn()` route through here), and the tracker guard makes it run once. Mirrors the
            // spawn FX in CloudPlacer.placeCloud. `despawn_ticks == 0` clouds discard before reaching
            // DESPAWNING, so they get no wind-down FX by design.
            if (phase == PHASE_DESPAWNING) {
                playDespawnFX();
            }
        }
        int endOfPhaseAge = switch (phase) {
            case PHASE_SPAWNING   -> spawnDuration;
            case PHASE_ACTIVE     -> timeToLive - despawnDuration;
            case PHASE_DESPAWNING -> timeToLive;
            default               -> 0;
        };
        getDataTracker().set(END_OF_PHASE_AGE, endOfPhaseAge);
    }

    // MARK: Animation states
    // Exposed for renderers that want to animate the lifecycle — e.g. scale-in across the spawning
    // phase and scale-out across the despawning phase. Driven client-side each tick from the synced
    // phase, mirroring SummonedEntity. A renderer reads `getTimeRunning()` / `getTimeInMilliseconds()`
    // off the relevant state to map duration progress onto a keyframed animation.

    public final AnimationState spawnAnimationState   = new AnimationState();
    // Runs throughout the ACTIVE phase. Named `idle` (not `active`) to match the summon-model
    // convention (see FrostElementalModel), so a cloud model can drive it the same way.
    public final AnimationState idleAnimationState    = new AnimationState();
    public final AnimationState despawnAnimationState = new AnimationState();

    // Client-side render basis for all size interpolation. RADIUS_TRACKER is the single source of truth
    // for the cloud's true radius (base + power + modifiers + growth — whatever the source); these
    // snapshot it each tick so the renderer can lerp across `tickDelta`. `spawnRenderRadius` is the
    // scale-1.0 reference (first observed radius), so any later change scales the model identically no
    // matter where it came from.
    private float spawnRenderRadius = -1F;
    private float prevRenderRadius = -1F;
    private float renderRadius = -1F;

    /// Client-side model scale, interpolated within the tick so a growing cloud animates smoothly. Derived
    /// solely from the synced radius relative to the spawn radius, so it is 1.0 for any cloud whose radius
    /// never changes and scales identically whether the growth is configured on the spell or added by a
    /// modifier.
    public float getRenderScale(float tickDelta) {
        if (spawnRenderRadius <= 0F) return 1F;
        return net.minecraft.util.math.MathHelper.lerp(tickDelta, prevRenderRadius, renderRadius) / spawnRenderRadius;
    }

    /// Client-side: keeps the three lifecycle animation states in sync with the current phase.
    private void setupAnimationStates() {
        spawnAnimationState.setRunning(isSpawning(), this.age);
        boolean despawning = isDespawning();
        if (despawning && !despawnAnimationState.isRunning()) {
            // END_OF_PHASE_AGE = timeToLive during DESPAWNING (a future absolute age). Starting the
            // state there makes getTimeRunning() begin negative, so a renderer can play the spawn clip
            // at speedMultiplier=-1F to scale back out as despawn completes (same trick as SummonedEntity).
            despawnAnimationState.start(getDataTracker().get(END_OF_PHASE_AGE));
        } else if (!despawning) {
            despawnAnimationState.stop();
        }
        idleAnimationState.setRunning(isActive(), this.age);
    }

    /// Server-side: emit the cloud's configured despawn FX (sound, particles, model effects), broadcast
    /// to nearby clients. Symmetrical with the spawn FX in `CloudPlacer.placeCloud`.
    private void playDespawnFX() {
        var cloudData = getCloudData();
        if (cloudData == null) {
            return;
        }
        var despawn = cloudData.despawn;
        if (despawn.sound != null) {
            SoundHelper.playSound(getEntityWorld(), this, despawn.sound);
        }
        var despawnVisuals = despawn.visuals.resolved(Fx.Context.NONE);
        ParticleHelper.sendBatches(this, despawnVisuals.particles);
        ModelEffectHelper.spawn(getEntityWorld(), this.getEntityPos(), this.getYaw(), despawnVisuals.models, null);
    }

    /// The cloud's radius before any lifetime growth: the configured base plus power scaling, plus the
    /// summed `radius_add` from cloud-targeting spell modifiers.
    private float baseRadius() {
        var cloudData = getCloudData();
        if (cloudData != null) {
            var radius = cloudData.volume.radius;
            if (context != null) {
                radius = cloudData.volume.combinedRadius(context.power().baseValue());
            }
            return Math.max(radius + cloudModifier.radius_add, 0F);
        } else {
            return 0F;
        }
    }

    /// The effective radius at a given age, applying `growth` on top of `baseRadius()`. A pure function
    /// of age (base is age-independent), so it needs no extra synced/persisted state and stays consistent
    /// across an NBT round-trip.
    ///
    /// Growth combines the cloud's own `growth` with the summed modifier contribution: magnitudes
    /// (`radius_step`, `duration_ticks`) sum, so modifiers stack and can attach growth to a bare cloud;
    /// timing (`step_interval`, `start_tick`) comes from the cloud when it already grows, otherwise from
    /// the modifier. Growth is off unless the effective `radius_step` and `duration_ticks` are both
    /// non-zero; a negative `duration_ticks` (on either side) means "whole life". Floored at 0 so
    /// shrinking can't go negative.
    private float radiusForAge(int age) {
        var base = baseRadius();
        var cloudData = getCloudData();
        if (cloudData == null) {
            return base;
        }
        var growth = cloudData.growth;
        var mod = cloudModifier.growth;
        boolean baseGrows = growth != null && growth.radius_step != 0F && growth.duration_ticks != 0;

        float step = (growth != null ? growth.radius_step : 0F) + mod.radius_step;
        int baseDuration = growth != null ? growth.duration_ticks : 0;
        // A negative duration on either side is the "whole life" sentinel — it wins over any summed span.
        int duration = (baseDuration < 0 || mod.duration_ticks < 0) ? -1 : baseDuration + mod.duration_ticks;
        int interval = baseGrows ? growth.step_interval : mod.step_interval;
        int startTick = baseGrows ? growth.start_tick : mod.start_tick;

        if (step == 0F || duration == 0 || interval <= 0) {
            return base;
        }
        int elapsed = age - startTick;
        if (elapsed <= 0) {
            return base;
        }
        if (duration > 0) {
            elapsed = Math.min(elapsed, duration);
        }
        int steps = elapsed / interval;
        return Math.max(base + steps * step, 0F);
    }

    public EntityDimensions getDimensions(EntityPose pose) {
        var cloudData = getCloudData();
        if (cloudData != null) {
            var radius = getDataTracker().get(RADIUS_TRACKER);
            var heightMultiplier = cloudData.volume.area.vertical_range_multiplier;
            return EntityDimensions.changing(radius * 2, radius * heightMultiplier);
        } else {
            return super.getDimensions(pose);
        }
    }

    // MARK: Owner

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = owner;
        this.ownerUuid = owner == null ? null : owner.getUuid();
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.owner == null && this.ownerUuid != null && this.getEntityWorld() instanceof ServerWorld) {
            Entity entity = ((ServerWorld)this.getEntityWorld()).getEntity(this.ownerUuid);
            if (entity instanceof LivingEntity) {
                this.owner = (LivingEntity)entity;
            }
        }
        return this.owner;
    }

    // MARK: Sync

    private static final TrackedData<String> SPELL_ID_TRACKER  = DataTracker.registerData(SpellCloud.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> DATA_INDEX_TRACKER = DataTracker.registerData(SpellCloud.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> RADIUS_TRACKER = DataTracker.registerData(SpellCloud.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Byte> PHASE_TRACKER = DataTracker.registerData(SpellCloud.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> END_OF_PHASE_AGE = DataTracker.registerData(SpellCloud.class, TrackedDataHandlerRegistry.INTEGER);

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SPELL_ID_TRACKER, "");
        builder.add(DATA_INDEX_TRACKER, this.dataIndex);
        builder.add(RADIUS_TRACKER, 0F);
        builder.add(PHASE_TRACKER, PHASE_SPAWNING);
        builder.add(END_OF_PHASE_AGE, 0);
    }

    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (getEntityWorld().isClient()) {
            var rawSpellId = this.getDataTracker().get(SPELL_ID_TRACKER);
            if (rawSpellId != null && !rawSpellId.isEmpty()) {
                this.spellId = Identifier.of(rawSpellId);
            }
            this.dataIndex = this.getDataTracker().get(DATA_INDEX_TRACKER);
            this.calculateDimensions();
        }
    }

    // MARK: Persistence

    private enum NBTKey {
        AGE("Age"),
        TIME_TO_LIVE("TTL"),
        SPAWN_DURATION("SpawnDuration"),
        DESPAWN_DURATION("DespawnDuration"),
        SPELL_ID("SpellId"),
        DATA_INDEX("DataIndex"),
        CLOUD_MODIFIER("CloudMod")
        ;

        public final String key;
        NBTKey(String key) {
            this.key = key;
        }
    }

    @Override
    protected void readCustomData(ReadView view) {
        this.age = view.getInt(NBTKey.AGE.key, 0);
        this.timeToLive = view.getInt(NBTKey.TIME_TO_LIVE.key, 0);
        this.spawnDuration = view.getInt(NBTKey.SPAWN_DURATION.key, 0);
        this.despawnDuration = view.getInt(NBTKey.DESPAWN_DURATION.key, 0);
        this.spellId = Identifier.of(view.getString(NBTKey.SPELL_ID.key, ""));
        this.dataIndex = view.getInt(NBTKey.DATA_INDEX.key, 0);
        view.getOptionalReadView(NBTKey.CLOUD_MODIFIER.key).ifPresent(cm -> {
            var modifier = new Spell.Modifier.Cloud();
            modifier.radius_add = cm.getFloat("RadiusAdd", 0);
            modifier.growth.radius_step = cm.getFloat("GrowthStep", 0);
            modifier.growth.step_interval = cm.getInt("GrowthInterval", 0);
            modifier.growth.start_tick = cm.getInt("GrowthStart", 0);
            modifier.growth.duration_ticks = cm.getInt("GrowthDuration", 0);
            this.cloudModifier = modifier;
        });
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.putInt(NBTKey.AGE.key, this.age);
        view.putInt(NBTKey.TIME_TO_LIVE.key, this.timeToLive);
        view.putInt(NBTKey.SPAWN_DURATION.key, this.spawnDuration);
        view.putInt(NBTKey.DESPAWN_DURATION.key, this.despawnDuration);
        view.putString(NBTKey.SPELL_ID.key, this.spellId.toString());
        view.putInt(NBTKey.DATA_INDEX.key, this.dataIndex);
        var cm = view.get(NBTKey.CLOUD_MODIFIER.key);
        cm.putFloat("RadiusAdd", cloudModifier.radius_add);
        cm.putFloat("GrowthStep", cloudModifier.growth.radius_step);
        cm.putInt("GrowthInterval", cloudModifier.growth.step_interval);
        cm.putInt("GrowthStart", cloudModifier.growth.start_tick);
        cm.putInt("GrowthDuration", cloudModifier.growth.duration_ticks);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    // MARK: Behavior

    @Override
    public boolean isSilent() {
        return false;
    }
    private boolean presenceSoundFired = false;

    public void tick() {
        super.tick();
        var cloudData = this.getCloudData();
        if (cloudData == null) {
            // this.discard();
            return;
        }
        var world = this.getEntityWorld();
        if (world.isClient()) {
            // Client side tick
            float trackedRadius = getDataTracker().get(RADIUS_TRACKER);
            if (spawnRenderRadius < 0F) {
                spawnRenderRadius = prevRenderRadius = renderRadius = trackedRadius;
            } else {
                prevRenderRadius = renderRadius;
                renderRadius = trackedRadius;
            }
            setupAnimationStates();
            var clientData = cloudData.client_data;
            var spawnParticles = clientData.particle_spawn_interval <= 1 || (this.age % clientData.particle_spawn_interval) == 0;
            if (spawnParticles) {
                for (var particleBatch : clientData.interval_particles) {
                    ParticleHelper.play(world, this, particleBatch);
                }
            }
            for (var particleBatch : clientData.particles) {
                ParticleHelper.play(world, this, particleBatch);
            }

            var presence_sound = cloudData.presence_sound;
            if (!presenceSoundFired && presence_sound != null) {
                var soundEvent = Registries.SOUND_EVENT.get(Identifier.of(presence_sound.id()));
                if (soundEvent != null) {
                    ((SoundPlayerWorld) world).playSoundFromEntity(this, soundEvent, SoundCategory.PLAYERS,
                            presence_sound.volume(),
                            presence_sound.randomizedPitch());
                    presenceSoundFired = true;
                } else {
                    System.out.println("SpellCloud: Failed to find presence sound " + presence_sound.id());
                }
            }

        } else {
            // Server side tick
            if (this.age >= this.timeToLive) {
                this.discard();
                return;
            }
            // Advance the lifecycle phase. Impacts are only performed while ACTIVE — the
            // spawning and despawning phases are warm-up / wind-down, matching SummonedEntity.
            byte phase;
            if (this.age < spawnDuration) {
                phase = PHASE_SPAWNING;
            } else if (this.age >= timeToLive - despawnDuration) {
                phase = PHASE_DESPAWNING;
            } else {
                phase = PHASE_ACTIVE;
            }
            setPhase(phase);
            // Lifetime growth: recompute the radius from the current age and republish it only when it
            // actually changes (i.e. on a growth-step boundary), so rendering/collision follow and sync
            // traffic stays minimal. `radiusForAge` is a no-op unless `growth` is configured.
            float grownRadius = radiusForAge(this.age);
            if (grownRadius != getDataTracker().get(RADIUS_TRACKER)) {
                getDataTracker().set(RADIUS_TRACKER, grownRadius);
                calculateDimensions();
            }
            if (phase == PHASE_ACTIVE && (this.age % cloudData.impact_tick_interval) == 0) {
                // Impact tick due
                var area_impact = cloudData.volume;
                var owner = (LivingEntity) this.getOwner();
                var spellEntry = getSpellEntry();
                if (area_impact != null && owner != null && spellEntry != null) {
                    var spell = spellEntry.value();
                    var context = this.context;
                    if (context == null) {
                        context = new SpellExecution.ImpactContext();
                    }
                    var performed = SpellImpacts.lookupAndPerformAreaImpact(area_impact, spellEntry, owner,null,
                            this, spell.impacts, context.position(this.getEntityPos()), true, grownRadius);
                    if (performed) {
                        onImpactPerformed(owner, world, cloudData, context);
                        if (this.impactCap > 0 && this.impactsPerformed >= this.impactCap) {
                            // A spent cloud winds down through DESPAWNING rather than vanishing on
                            // the spot, so its model gets a phase to animate in. A subclass that
                            // already called `beginDespawn` from `onImpactPerformed` (e.g. to use a
                            // longer, bespoke clip) makes this a no-op.
                            beginDespawn(this.despawnDuration);
                        }
                    } else {
                        onImpactFailed(owner, world, cloudData, context);
                    }
                }
            }
        }
    }

    protected void onImpactPerformed(LivingEntity owner, World world, Spell.Delivery.Cloud cloudData, SpellExecution.ImpactContext context) {
        // Server-side call site: `ParticleHelper.play` bottoms out in `World.addParticle`, which is an
        // empty method on anything but ClientWorld — it has to be broadcast instead. Detached (rather
        // than entity-anchored) because a cloud whose `despawn_ticks` is 0 is discarded on this very
        // tick, and the client would resolve the packet's entity id to nothing.
        ParticleHelper.sendBatchesDetached(this, cloudData.impact.resolved(Fx.Context.NONE).particles);
        this.impactsPerformed++;
    }

    protected void onImpactFailed(LivingEntity owner, World world, Spell.Delivery.Cloud cloudData, SpellExecution.ImpactContext context) {
        // No-op by default; override in subclasses to handle failed impacts (e.g. play a sound).
    }

    @Nullable public Spell.Delivery.Cloud getCloudData() {
        var spellEntry = getSpellEntry();
        if (spellEntry != null) {
            var spell = spellEntry.value();
            return spell.deliver.clouds.get(dataIndex);
        }
        return null;
    }

    @Nullable public RegistryEntry<Spell> getSpellEntry() {
        return SpellRegistry.from(this.getEntityWorld()).getEntry(this.spellId).orElse(null);
    }
}
