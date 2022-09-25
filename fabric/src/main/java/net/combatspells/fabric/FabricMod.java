package net.combatspells.fabric;

import net.combatspells.CombatSpells;
import net.fabricmc.api.ModInitializer;
import net.combatspells.utils.SoundHelper;

public class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CombatSpells.init();
        SoundHelper.registerSounds();
    }
}