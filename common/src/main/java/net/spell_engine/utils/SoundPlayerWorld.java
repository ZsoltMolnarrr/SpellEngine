package net.spell_engine.utils;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public interface SoundPlayerWorld {
    void playSoundFromEntity(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch);
}
