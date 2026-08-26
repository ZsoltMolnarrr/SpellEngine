package net.spell_engine.client.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;

public class SpellCastingSound extends SimpleSoundInstance implements SoundInstance, TickableSoundInstance {
    public interface Listener {
        void onSpellCastingSoundDone();
    }

    private LivingEntity emitter;
    private boolean done;
    public @Nullable Listener listener;

    public SpellCastingSound(LivingEntity emitter, Identifier id, float volume, float pitch) {
        super(id, SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), true, 0, Attenuation.LINEAR,
                emitter.getX(), emitter.getY(), emitter.getZ(), false);
        this.emitter = emitter;
    }

    private boolean isEmitterCasting() {
        return emitter != null && emitter.isAlive() && (emitter instanceof SpellCaster.Entity caster && caster.isCastingSpell());
    }

    @Override
    public boolean isStopped() {
        return done;
    }

    protected final void setDone() {
        this.done = true;
        this.looping = false;
        this.volume = 0;
        if (listener != null) {
            listener.onSpellCastingSoundDone();
        }
    }

    @Override
    public void tick() {
        if (!isEmitterCasting()) {
            setDone();
        }
    }

    @Override
    public double getX() {
        return emitter.getX();
    }

    @Override
    public double getY() {
        return emitter.getY();
    }

    @Override
    public double getZ() {
        return emitter.getZ();
    }
}
