package net.combatspells.utils;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.combatspells.CombatSpells;

import java.util.List;

public class SoundHelper {
    public static List<String> soundKeys = List.of(
        "roll",
        "roll_cooldown_ready"
    );

    public static void registerSounds() {
        for (var soundKey: soundKeys) {
            var soundId = new Identifier(CombatSpells.MOD_ID, soundKey);
            var soundEvent = new SoundEvent(soundId);
            Registry.register(Registry.SOUND_EVENT, soundId, soundEvent);
        }
    }
}
