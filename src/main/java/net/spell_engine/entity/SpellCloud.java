package net.spell_engine.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.particle.ParticleHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class SpellCloud extends Entity implements Ownable {
    public static EntityType<SpellCloud> ENTITY_TYPE;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID ownerUuid;
    private int timeToLive;
    private Identifier spellId;
    private int dataIndex = 0;
    private SpellHelper.ImpactContext context;

    public SpellCloud(EntityType<? extends SpellCloud> entityType, World world) {
        super(entityType, world);
    }

    public SpellCloud(World world) {
        super(ENTITY_TYPE, world);
        this.noClip = true;
    }

    public void onCreatedFromSpell(Identifier spellId, Spell.Release.Target.Cloud cloudData, SpellHelper.ImpactContext context) {
        this.spellId = spellId;
        this.getDataTracker().set(SPELL_ID_TRACKER, this.spellId.toString());

        var spell = getSpell();
        if (spell != null) {
            var index = -1;
            var dataList = List.of(spell.release.target.clouds);
            if (!dataList.isEmpty()) {
                index = dataList.indexOf(cloudData);
            }
            this.dataIndex = index;
        }
        this.getDataTracker().set(DATA_INDEX_TRACKER, this.dataIndex);

        this.context = context;
        this.timeToLive = (int) (cloudData.time_to_live_seconds * 20);
    }

    public EntityDimensions getDimensions(EntityPose pose) {
        var cloudData = getCloudData();
        if (cloudData != null) {
            var radius = cloudData.volume.radius;
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

    @Override
    protected void initDataTracker() {
        this.getDataTracker().startTracking(SPELL_ID_TRACKER, "");
        this.getDataTracker().startTracking(DATA_INDEX_TRACKER, this.dataIndex);
    }

    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        var rawSpellId = this.getDataTracker().get(SPELL_ID_TRACKER);
        if (rawSpellId != null && !rawSpellId.isEmpty()) {
            this.spellId = new Identifier(rawSpellId);
        }
        this.dataIndex = this.getDataTracker().get(DATA_INDEX_TRACKER);
        this.calculateDimensions();
    }

    // MARK: Persistence

    private enum NBTKey {
        AGE("Age"),
        TIME_TO_LIVE("TTL"),
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
        this.spellId = new Identifier(nbt.getString(NBTKey.SPELL_ID.key));
        this.dataIndex = nbt.getInt(NBTKey.DATA_INDEX.key);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt(NBTKey.AGE.key, this.age);
        nbt.putInt(NBTKey.TIME_TO_LIVE.key, this.timeToLive);
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
        if (this.getWorld().isClient) {
            // Client side tick
            var clientData = cloudData.client_data;
            for (var particleBatch : clientData.particles) {
                ParticleHelper.play(this.getWorld(), this, particleBatch);
            }
            var presence_sound = cloudData.presence_sound;
            if (!presenceSoundFired && presence_sound != null) {
                var clientWorld = (ClientWorld) this.getWorld();
                var player = MinecraftClient.getInstance().player;
                var soundEvent = SoundEvent.of(new Identifier(presence_sound.id()));
                clientWorld.playSoundFromEntity(player, this, soundEvent, SoundCategory.PLAYERS,
                        presence_sound.volume(),
                        presence_sound.randomizedPitch());
                presenceSoundFired = true;
            }

        } else {
            // Server side tick
            if (this.age >= this.timeToLive) {
                this.discard();
                return;
            }
            if ((this.age % cloudData.impact_tick_interval) == 0) {
                // Impact tick due
                var area_impact = cloudData.volume;
                var owner = (LivingEntity) this.getOwner();
                var spell = getSpell();
                if (area_impact != null && owner != null && spell != null) {
                    var context = this.context;
                    if (context == null) {
                        context = new SpellHelper.ImpactContext();
                    }
                    SpellHelper.lookupAndPerformAreaImpact(area_impact, new SpellInfo(spell, spellId), owner,null,
                            this, context.position(this.getPos()), true);
                }
            }
        }
    }

    @Nullable public Spell.Release.Target.Cloud getCloudData() {
        var spell = this.getSpell();
        if (spell != null) {
            if (spell.release.target.clouds.length > 0) {
                return spell.release.target.clouds[dataIndex];
            } else {
                return spell.release.target.cloud;
            }
        }
        return null;
    }

    public Spell getSpell() {
        return SpellRegistry.getSpell(spellId);
    }
}
