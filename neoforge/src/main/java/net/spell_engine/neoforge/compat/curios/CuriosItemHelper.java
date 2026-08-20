package net.spell_engine.neoforge.compat.curios;

import net.spell_engine.compat.SlotModCompat;
import net.spell_engine.fx.SpellEngineSounds;

/**
 * Outsourced to avoid classloading issues if Curios is not present.
 */
public class CuriosItemHelper {
    public static void register() {
        SlotModCompat.setSpellScrollFactory(
                (args) -> new SpellScrollCurioItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
        SlotModCompat.setSpellBookFactory(
                (args) -> new SpellBookCurioItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
    }
}
