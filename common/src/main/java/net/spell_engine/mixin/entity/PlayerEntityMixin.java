package net.spell_engine.mixin.entity;

import com.google.gson.Gson;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.internals.*;
import net.spell_engine.internals.arrow.ArrowShootContext;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCastSyncHelper;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.spell_engine.api.spell.Spell.Target.Type.BEAM;

@Mixin(value = PlayerEntity.class, priority = 555)
public class PlayerEntityMixin implements SpellCasterEntity {

    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }

    private final SpellCooldownManager spellCooldownManager = new SpellCooldownManager(player());

    private static final TrackedData<String> SPELL_ENGINE_SPELL_PROGRESS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final Gson syncGson = new Gson();

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SPELL_ENGINE_SPELL_PROGRESS, "");
    }

    private SpellCast.Process synchronizedSpellCastProcess = null;
    public void setSpellCastProcess(@Nullable SpellCast.Process process) {
        if (process != null && process.spell().value().active == null) { return; }
        synchronizedSpellCastProcess = process;
        var json = process != null ? process.fastSyncJSON() : "";
        player().getDataTracker().set(SPELL_ENGINE_SPELL_PROGRESS, json);
    }

    private int channelTickIndex = 0;
    @Override
    public void setChannelTickIndex(int channelTickIndex) {
        this.channelTickIndex = channelTickIndex;
    }
    @Override
    public int getChannelTickIndex() {
        return channelTickIndex;
    }

    @Nullable public SpellCast.Process getSpellCastProcess() {
        return synchronizedSpellCastProcess;
    }

    @Override
    public Spell getCurrentSpell() {
        var process = getSpellCastProcess();
        if (process != null) {
            return process.spell().value();
        }
        return null;
    }

    @Override
    public float getCurrentCastingSpeed() {
        var process = getSpellCastProcess();
        if (process != null) {
            return process.speed();
        }
        return 1F; // Fallback value
    }

    private ArrowShootContext arrowShotContext = ArrowShootContext.EMPTY;
    @Override
    public void setArrowShootContext(ArrowShootContext shotContext) {
        arrowShotContext = shotContext;
    }
    @Override
    public ArrowShootContext getArrowShootContext() {
        return arrowShotContext;
    }

    @Override
    public SpellCooldownManager getCooldownManager() {
        return spellCooldownManager;
    }

    private String lastHandledSyncData = "";

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        if (player.getWorld().isClient) {
            ((AnimatablePlayer)player()).updateSpellCastAnimationsOnTick();

            // Check changes in tracked data
            var progressString = player.getDataTracker().get(SPELL_ENGINE_SPELL_PROGRESS);
            if (!progressString.equals(lastHandledSyncData)) {
                if (progressString.isEmpty()) {
                    this.synchronizedSpellCastProcess = null;
                } else {
                    var syncFormat = syncGson.fromJson(progressString, SpellCast.Process.SyncFormat.class);
                    this.synchronizedSpellCastProcess = SpellCast.Process.fromSync(player.getWorld(), syncFormat, player.getMainHandStack().getItem(), player.getWorld().getTime());
                }
                lastHandledSyncData = progressString;
            }

        } else {
            // Server side
            if (synchronizedSpellCastProcess != null) {
                var castTicks = synchronizedSpellCastProcess.spellCastTicksSoFar(player.getWorld().getTime());
                if (castTicks >= (synchronizedSpellCastProcess.length() * 1.5)) {
                    SpellCastSyncHelper.clearCasting(player);
                }
            }
        }
        spellCooldownManager.tickUpdate();
    }

    public boolean isBeaming() {
        return getBeam() != null;
    }

    @Nullable
    public Spell.Target.Beam getBeam() {
        var spell = getCurrentSpell();
        if (spell != null && spell.target != null && spell.target.type == BEAM) {
            return spell.target.beam;
        }
        return null;
    }

    // MARK: Persistence

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void writeCustomDataToNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        spellCooldownManager.writeCustomDataToNbt(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void readCustomDataFromNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        spellCooldownManager.readCustomDataFromNbt(nbt);
    }
}
