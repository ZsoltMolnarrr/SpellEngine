package net.spell_engine.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Sound;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;

import java.util.List;
import java.util.Map;

public class SoundHelper {
    public static List<String> soundKeys = List.of(
        "generic_arcane_casting",
        "generic_arcane_release",

        "generic_fire_casting",
        "generic_fire_release",

        "generic_frost_casting",
        "generic_frost_release",
        "generic_frost_impact",

        "generic_healing_casting",
        "generic_healing_release",
        "generic_healing_impact_1",
        "generic_healing_impact_2",

        "generic_lightning_casting",
        "generic_lightning_release",

        "generic_soul_casting",
        "generic_soul_release"
    );

    public static Map<String, Float> soundDistances = Map.of(
        // "fire_meteor_impact", Float.valueOf(48F)
    );

    public static void registerSounds() {
        for (var soundKey: soundKeys) {
            var soundId = new Identifier(SpellEngineMod.ID, soundKey);
            var customTravelDistance = soundDistances.get(soundKey);
            var soundEvent = (customTravelDistance == null)
                    ? SoundEvent.of(soundId)
                    : SoundEvent.of(soundId, customTravelDistance);
            Registry.register(Registries.SOUND_EVENT, soundId, soundEvent);
        }
        Registry.register(Registries.SOUND_EVENT, SpellBindingScreenHandler.soundId, SpellBindingScreenHandler.soundEvent);
    }

    public static void playSound(World world, Entity entity, Sound sound) {
        if (sound == null) {
            return;
        }
        try {
            var soundEvent = Registries.SOUND_EVENT.get(new Identifier(sound.id()));
            playSoundEvent(world, entity, soundEvent, sound.volume(), sound.randomizedPitch());
        } catch (Exception e) {
            System.err.println("Failed to play sound: " + sound.id());
            e.printStackTrace();
        }
    }

    public static void playSoundEvent(World world, Entity entity, SoundEvent soundEvent) {
        playSoundEvent(world, entity, soundEvent, 1, 1);
    }

    public static void playSoundEvent(World world, Entity entity, SoundEvent soundEvent, float volume, float pitch) {
        world.playSound(
                (PlayerEntity)null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                soundEvent,
                SoundCategory.PLAYERS,
                volume,
                pitch);
    }
}
