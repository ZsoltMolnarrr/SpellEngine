package net.spell_engine.rpg_series.client;

import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.rpg_series.datagen.WeaponSkills;

public class RPGSeriesCoreClient {
    public static void init() {
        for (var entry: WeaponSkills.entries) {
            if (entry.mutator() != null) {
                SpellTooltip.addDescriptionMutator(entry.id(), entry.mutator());
            }
        }
    }
}
