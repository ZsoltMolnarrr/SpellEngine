package net.spell_engine.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.SoundPlayerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
    private SpellHelper.ImpactContext context;

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

    public void onCreatedFromSpell(Identifier spellId, Spell.Delivery.Cloud cloudData, SpellHelper.ImpactContext context, float time_to_live_seconds) {
        this.spellId = spellId;
        this.context = context;

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
        this.getDataTracker().set(RADIUS_TRACKER, calculateRadius());

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
        if (getWorld().isClient || isDespawning()) {
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
            // spawn FX in SpellHelper.spawnClouds. `despawn_ticks == 0` clouds discard before reaching
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
    /// to nearby clients. Symmetrical with the spawn FX in `SpellHelper.spawnClouds`.
    private void playDespawnFX() {
        var cloudData = getCloudData();
        if (cloudData == null) {
            return;
        }
        var despawn = cloudData.despawn;
        if (despawn.sound != null) {
            SoundHelper.playSound(getWorld(), this, despawn.sound);
        }
        var despawnVisuals = despawn.visuals.resolved(Fx.Context.NONE);
        ParticleHelper.sendBatches(this, despawnVisuals.particles);
        ModelEffectHelper.spawn(getWorld(), this.getPos(), this.getYaw(), despawnVisuals.models, null);
    }

    private float calculateRadius() {
        var cloudData = getCloudData();
        if (cloudData != null) {
            var radius = cloudData.volume.radius;
            if (context != null) {
                radius = cloudData.volume.combinedRadius(context.power().baseValue());
            }
            return radius;
        } else {
            return 0F;
        }
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
        if (this.owner == null && this.ownerUuid != null && this.getWorld() instanceof ServerWorld) {
            Entity entity = ((ServerWorld)this.getWorld()).getEntity(this.ownerUuid);
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
        if (getWorld().isClient) {
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
        DATA_INDEX("DataIndex")
        ;

        public final String key;
        NBTKey(String key) {
            this.key = key;
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.age = nbt.getInt(NBTKey.AGE.key);
        this.timeToLive = nbt.getInt(NBTKey.TIME_TO_LIVE.key);
        this.spawnDuration = nbt.getInt(NBTKey.SPAWN_DURATION.key);
        this.despawnDuration = nbt.getInt(NBTKey.DESPAWN_DURATION.key);
        this.spellId = Identifier.of(nbt.getString(NBTKey.SPELL_ID.key));
        this.dataIndex = nbt.getInt(NBTKey.DATA_INDEX.key);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt(NBTKey.AGE.key, this.age);
        nbt.putInt(NBTKey.TIME_TO_LIVE.key, this.timeToLive);
        nbt.putInt(NBTKey.SPAWN_DURATION.key, this.spawnDuration);
        nbt.putInt(NBTKey.DESPAWN_DURATION.key, this.despawnDuration);
        nbt.putString(NBTKey.SPELL_ID.key, this.spellId.toString());
        nbt.putInt(NBTKey.DATA_INDEX.key, this.dataIndex);
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
        var world = this.getWorld();
        if (world.isClient) {
            // Client side tick
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
            if (phase == PHASE_ACTIVE && (this.age % cloudData.impact_tick_interval) == 0) {
                // Impact tick due
                var area_impact = cloudData.volume;
                var owner = (LivingEntity) this.getOwner();
                var spellEntry = getSpellEntry();
                if (area_impact != null && owner != null && spellEntry != null) {
                    var spell = spellEntry.value();
                    var context = this.context;
                    if (context == null) {
                        context = new SpellHelper.ImpactContext();
                    }
                    var performed = SpellHelper.lookupAndPerformAreaImpact(area_impact, spellEntry, owner,null,
                            this, spell.impacts, context.position(this.getPos()), true);
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

    protected void onImpactPerformed(LivingEntity owner, World world, Spell.Delivery.Cloud cloudData, SpellHelper.ImpactContext context) {
        // Server-side call site: `ParticleHelper.play` bottoms out in `World.addParticle`, which is an
        // empty method on anything but ClientWorld — it has to be broadcast instead. Detached (rather
        // than entity-anchored) because a cloud whose `despawn_ticks` is 0 is discarded on this very
        // tick, and the client would resolve the packet's entity id to nothing.
        ParticleHelper.sendBatchesDetached(this, cloudData.impact.resolved(Fx.Context.NONE).particles);
        this.impactsPerformed++;
    }

    protected void onImpactFailed(LivingEntity owner, World world, Spell.Delivery.Cloud cloudData, SpellHelper.ImpactContext context) {
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
        return SpellRegistry.from(this.getWorld()).getEntry(this.spellId).orElse(null);
    }
}
