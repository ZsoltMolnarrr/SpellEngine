package net.spell_engine.client.sound;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.jetbrains.annotations.Nullable;

public class SpellCastingSound extends PositionedSoundInstance implements SoundInstance, TickableSoundInstance {
    public interface Listener {
        void onSpellCastingSoundDone();
    }

    private LivingEntity emitter;
    private boolean done;
    public @Nullable Listener listener;

    public SpellCastingSound(LivingEntity emitter, Identifier id, float volume, float pitch) {
        super(id, SoundCategory.PLAYERS, volume, pitch,
                SoundInstance.createRandom(), true, 0, AttenuationType.LINEAR,
                emitter.getX(), emitter.getY(), emitter.getZ(), false);
        this.emitter = emitter;
    }

    private boolean isEmitterCasting() {
        return emitter != null && emitter.isAlive() && (emitter instanceof SpellCasterEntity caster && caster.isCastingSpell());
    }

    @Override
    public boolean isDone() {
        return done;
    }

    protected final void setDone() {
        this.done = true;
        this.repeat = false;
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
