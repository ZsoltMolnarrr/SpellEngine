package net.spell_engine.entity;

import com.google.gson.Gson;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.spell_engine.api.spell.fx.ModelEffect;
import org.jetbrains.annotations.Nullable;

public class SpellModelEffect extends Entity {
    public static EntityType<SpellModelEffect> ENTITY_TYPE;

    private static final Gson gson = new Gson();
    private static final EntityDataAccessor<String> MODEL_EFFECT_DATA = SynchedEntityData.defineId(SpellModelEffect.class, EntityDataSerializers.STRING);

    @Nullable
    private ModelEffect modelEffect;
    private int timeToLive = 20;

    public SpellModelEffect(EntityType<?> type, Level world) {
        super(type, world);
        this.noPhysics = true;
    }

    public SpellModelEffect(Level world) {
        super(ENTITY_TYPE, world);
        this.noPhysics = true;
    }

    public void setup(ModelEffect effect) {
        this.modelEffect = effect;
        this.timeToLive = effect.duration;
        this.getEntityData().set(MODEL_EFFECT_DATA, gson.toJson(effect));
    }

    @Nullable
    public ModelEffect getModelEffect() {
        return modelEffect;
    }

    // MARK: Sync

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL_EFFECT_DATA, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (level().isClientSide()) {
            var json = this.getEntityData().get(MODEL_EFFECT_DATA);
            if (json != null && !json.isEmpty()) {
                this.modelEffect = gson.fromJson(json, ModelEffect.class);
                if (this.modelEffect != null) {
                    this.timeToLive = this.modelEffect.duration;
                }
            }
        }
    }

    // MARK: Persistence

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        this.tickCount = view.getIntOr("Age", 0);
        this.timeToLive = view.getIntOr("TTL", 0);
        var json = view.getStringOr("ModelEffect", "");
        if (!json.isEmpty()) {
            this.modelEffect = gson.fromJson(json, ModelEffect.class);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        view.putInt("Age", this.tickCount);
        view.putInt("TTL", this.timeToLive);
        if (this.modelEffect != null) {
            view.putString("ModelEffect", gson.toJson(this.modelEffect));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    // MARK: Behavior

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && this.tickCount >= this.timeToLive) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
//        double d = this.getBoundingBox().getAverageSideLength() * 4.0;
//        if (Double.isNaN(d)) {
//            d = 4.0;
//        }
//        d *= 64.0;
//        return distance < d * d;
        return true;
    }
}
