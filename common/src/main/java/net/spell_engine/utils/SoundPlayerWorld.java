package net.spell_engine.utils;

import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public interface SoundPlayerWorld {
    void playSoundFromEntity(Entity entity, SoundEvent sound, SoundCategory category, float volume, float pitch);
}
