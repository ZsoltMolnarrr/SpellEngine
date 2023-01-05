package net.spell_engine.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Sound;
import net.spell_engine.wizards.FrostShieldStatusEffect;

import java.util.List;
import java.util.Map;

public class SoundHelper {
    public static List<String> soundKeys = List.of(
        "bind_spell",
        "generic_arcane_casting",
        "generic_fire_casting",
        "generic_frost_casting",
        "generic_healing_casting",
        "generic_lightning_casting",
        "generic_soul_casting",
        "generic_arcane_release",
        "generic_fire_release",
        "generic_frost_release",
        "generic_lightning_release",
        "generic_healing_release",
        "generic_soul_release",

        // Spell specific impact sounds

        "arcane_missile_release",
        "arcane_missile_impact",
        "arcane_blast_release",
        "arcane_blast_impact",
        "arcane_beam_start",
        "arcane_beam_casting",
        "arcane_beam_impact",
        "arcane_beam_release",

        "fireball_impact",
        "fire_breath_start",
        "fire_breath_casting",
        "fire_breath_release",
        "fire_breath_impact",
        "fire_meteor_release",
        "fire_meteor_impact",

        "frostbolt_impact",
        "frost_nova_release",
        "frost_nova_damage_impact",
        "frost_nova_effect_impact",
        "frost_shield_release"
    );

    public static Map<String, Float> soundDistances = Map.of(
        "fire_meteor_impact", Float.valueOf(48F)
    );

    public static void registerSounds() {
        for (var soundKey: soundKeys) {
            var soundId = new Identifier(SpellEngineMod.ID, soundKey);
            var customTravelDistance = soundDistances.get(soundKey);
            var soundEvent = (customTravelDistance == null)
                    ? new SoundEvent(soundId)
                    : new SoundEvent(soundId, customTravelDistance);
            Registry.register(Registry.SOUND_EVENT, soundId, soundEvent);
        }

        // TODO: Remove
        Registry.register(Registry.SOUND_EVENT, FrostShieldStatusEffect.soundId, FrostShieldStatusEffect.sound);
    }

    public static void playSound(World world, Entity entity, Sound sound) {
        if (sound == null) {
            return;
        }
        try {
            var soundEvent = Registry.SOUND_EVENT.get(new Identifier(sound.id()));
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
