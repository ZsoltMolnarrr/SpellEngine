package net.spell_engine.neoforge.compat.curios;

import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvent;

/**
 * Universal spell book item for Curios mod compatibility.
 * Pool ID is derived from the SPELL_CONTAINER component, not stored in the item.
 */
public class SpellBookCurioItem extends SpellHostCurioItem {
    public SpellBookCurioItem(Item.Settings settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }
}
