package net.spell_engine.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.fx.Sound;

public class SoundHelper {
    public static void playSound(Level world, Entity entity, Sound sound) {
        if (sound == null) {
            return;
        }
        try {
            var soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(sound.id()));
            playSoundEvent(world, entity, soundEvent, sound.volume(), sound.randomizedPitch());
        } catch (Exception e) {
            System.err.println("Failed to play sound: " + sound.id());
            e.printStackTrace();
        }
    }

    public static void playSoundEvent(Level world, Entity entity, SoundEvent soundEvent) {
        playSoundEvent(world, entity, soundEvent, 1, 1);
    }

    public static void playSoundEvent(Level world, Entity entity, SoundEvent soundEvent, float volume, float pitch) {
        world.playSound(
                (Player)null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                soundEvent,
                SoundSource.PLAYERS,
                volume,
                pitch);
    }
}
