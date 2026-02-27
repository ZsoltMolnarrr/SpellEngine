package net.spell_engine.fabric.compat.trinkets;

import net.spell_engine.compat.SlotModCompat;
import net.spell_engine.fx.SpellEngineSounds;

/**
 * Outsourced to avoid classloading issues if Trinkets is not present.
 */
public class TrinketsItemHelper {
    public static void register() {
        SlotModCompat.setSpellScrollFactory(
                (args) -> new SpellScrollTrinketItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
        SlotModCompat.setSpellBookFactory(
                (args) -> new SpellBookTrinketItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
        SlotModCompat.spellBookResolver = TrinketsCompat::getSpellBookStack;
    }
}
