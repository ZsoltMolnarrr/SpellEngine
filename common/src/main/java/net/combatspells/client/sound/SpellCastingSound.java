package net.combatspells.client.sound;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public class SpellCastingSound extends PositionedSoundInstance implements SoundInstance, TickableSoundInstance {
    private LivingEntity emitter;
    private boolean done;

    public SpellCastingSound(LivingEntity emitter, Identifier id, float volume, float pitch) {
        super(id, SoundCategory.PLAYERS, volume, pitch,
                SoundInstance.createRandom(), true, 0, AttenuationType.LINEAR,
                emitter.getX(), emitter.getY(), emitter.getZ(), false);
        this.emitter = emitter;
    }

    private boolean isEmitterCasting() {
        return emitter != null && emitter.isAlive();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    protected final void setDone() {
        this.done = true;
        this.repeat = false;
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
