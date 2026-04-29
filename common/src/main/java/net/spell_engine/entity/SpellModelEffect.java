package net.spell_engine.entity;

import com.google.gson.Gson;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.ModelEffect;
import org.jetbrains.annotations.Nullable;

public class SpellModelEffect extends Entity {
    public static EntityType<SpellModelEffect> ENTITY_TYPE;

    private static final Gson gson = new Gson();
    private static final TrackedData<String> MODEL_EFFECT_DATA = DataTracker.registerData(SpellModelEffect.class, TrackedDataHandlerRegistry.STRING);

    @Nullable
    private ModelEffect modelEffect;
    private int timeToLive = 20;

    public SpellModelEffect(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public SpellModelEffect(World world) {
        super(ENTITY_TYPE, world);
        this.noClip = true;
    }

    public void setup(ModelEffect effect) {
        this.modelEffect = effect;
        this.timeToLive = effect.duration;
        this.getDataTracker().set(MODEL_EFFECT_DATA, gson.toJson(effect));
    }

    @Nullable
    public ModelEffect getModelEffect() {
        return modelEffect;
    }

    // MARK: Sync

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(MODEL_EFFECT_DATA, "");
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (getWorld().isClient) {
            var json = this.getDataTracker().get(MODEL_EFFECT_DATA);
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
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.age = nbt.getInt("Age");
        this.timeToLive = nbt.getInt("TTL");
        var json = nbt.getString("ModelEffect");
        if (!json.isEmpty()) {
            this.modelEffect = gson.fromJson(json, ModelEffect.class);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Age", this.age);
        nbt.putInt("TTL", this.timeToLive);
        if (this.modelEffect != null) {
            nbt.putString("ModelEffect", gson.toJson(this.modelEffect));
        }
    }

    // MARK: Behavior

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && this.age >= this.timeToLive) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRender(double distance) {
//        double d = this.getBoundingBox().getAverageSideLength() * 4.0;
//        if (Double.isNaN(d)) {
//            d = 4.0;
//        }
//        d *= 64.0;
//        return distance < d * d;
        return true;
    }
}
