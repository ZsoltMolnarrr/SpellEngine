package net.spell_engine.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.particle.ParticleHelper;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SpellCloud extends Entity implements Ownable {
    public static EntityType<SpellCloud> ENTITY_TYPE;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID ownerUuid;
    private int timeToLive;
    private Identifier spellId;
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
        this.context = context;
        this.timeToLive = (int) (cloudData.time_to_live_seconds * 20);
    }

    public EntityDimensions getDimensions(EntityPose pose) {
        var spell = getSpell();
        if (spell != null) {
            var cloudData = spell.release.target.cloud;
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

    @Override
    protected void initDataTracker() {
        this.getDataTracker().startTracking(SPELL_ID_TRACKER, "");
    }

    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        var rawSpellId = this.getDataTracker().get(SPELL_ID_TRACKER);
        if (rawSpellId != null && !rawSpellId.isEmpty()) {
            this.spellId = new Identifier(rawSpellId);
        }
        this.calculateDimensions();
    }

    // MARK: Persistence

    private enum NBTKey {
        AGE("Age"),
        TIME_TO_LIVE("TTL"),
        SPELL_ID("SpellId")
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
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt(NBTKey.AGE.key, this.age);
        nbt.putInt(NBTKey.TIME_TO_LIVE.key, this.timeToLive);
        nbt.putString(NBTKey.SPELL_ID.key, this.spellId.toString());
    }

    // MARK: Behavior

    public void tick() {
        super.tick();
        var spell = this.getSpell();
        if (spell == null) {
            // this.discard();
            return;
        }
        var cloudData = this.getSpell().release.target.cloud;
        if (this.getWorld().isClient) {
            // Client side tick
            var clientData = cloudData.client_data;
            for (var particleBatch : clientData.particles) {
                ParticleHelper.play(this.getWorld(), this, particleBatch);
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
                if (area_impact != null && owner != null) {
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

    public Spell getSpell() {
        return SpellRegistry.getSpell(spellId);
    }
}
