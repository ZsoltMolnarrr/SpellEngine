package net.spell_engine.compat.accessories;

import net.spell_engine.compat.SlotModCompat;
import net.spell_engine.fx.SpellEngineSounds;

/**
 * Outsource to avoid class loading SpellScrollAccessoryItem -> AccessoryItem
 * when Accessories mod is not present.
 * Accessories seems to have some unchecked type referencing.
 */
public class AccessoriesItemHelper {
    public static void register() {
        SlotModCompat.setSpellScrollFactory(
                (args) -> new SpellScrollAccessoryItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP::entry)
        );
        SlotModCompat.setSpellBookFactory(
                (args) -> new SpellBookAccessoryItem(args.settings(), SpellEngineSounds.SPELLBOOK_EQUIP::entry)
        );
        SlotModCompat.spellBookResolver = AccessoriesCompat::getSpellBookStack;
    }
}
