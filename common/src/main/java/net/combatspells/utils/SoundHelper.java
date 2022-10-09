package net.combatspells.utils;

import net.combatspells.CombatSpells;
import net.combatspells.api.spell.Sound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class SoundHelper {
    public static List<String> soundKeys = List.of(
        "casting_arcane",
        "casting_fire",
        "casting_frost",
        "release_arcane",
        "release_fire",
        "release_frost",
        // Spell specific impact sounds
        "impact_fireball",
        "impact_frostbolt"
    );

    public static void registerSounds() {
        for (var soundKey: soundKeys) {
            var soundId = new Identifier(CombatSpells.MOD_ID, soundKey);
            var soundEvent = new SoundEvent(soundId);
            Registry.register(Registry.SOUND_EVENT, soundId, soundEvent);
        }
    }

    public static void playSound(World world, Entity entity, Sound sound) {
        System.out.println("Release sound A");
        if (sound == null) {
            System.out.println("Release sound B");
            return;
        }
        try {
            System.out.println("Release sound C");
            var soundEvent = Registry.SOUND_EVENT.get(new Identifier(sound.id()));
            world.playSound(
                    (PlayerEntity)null,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    soundEvent,
                    SoundCategory.PLAYERS,
                    1F,
                    sound.randomizedPitch());
            System.out.println("Release sound D: " + " x:" + entity.getX() + " y:" + entity.getY() + " z:" + entity.getZ());
        } catch (Exception e) {
            System.out.println("Failed to play sound: " + sound.id());
            e.printStackTrace();
        }
    }

}
