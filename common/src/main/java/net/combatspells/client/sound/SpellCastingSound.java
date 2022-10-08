package net.combatspells.client.sound;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public class SpellCastingSound extends PositionedSoundInstance implements SoundInstance {
    private LivingEntity emitter;

    public SpellCastingSound(LivingEntity emitter, Identifier id, float volume, float pitch) {
        super(id, SoundCategory.PLAYERS, volume, pitch,
                SoundInstance.createRandom(), true, 0, AttenuationType.LINEAR,
//                emitter.getX(), emitter.getY(), emitter.getZ(), false);
                0, 0, 0, true);
        this.emitter = emitter;
    }

//    @Override
//    public double getX() {
//        return emitter.getX();
//    }
//
//    @Override
//    public double getY() {
//        return emitter.getY();
//    }
//
//    @Override
//    public double getZ() {
//        return emitter.getZ();
//    }
}
