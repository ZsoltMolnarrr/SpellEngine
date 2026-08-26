package net.spell_engine.neoforge.compat.curios;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * Universal spell book item for Curios mod compatibility.
 * Pool ID is derived from the SPELL_CONTAINER component, not stored in the item.
 */
public class SpellBookCurioItem extends SpellHostCurioItem {
    public SpellBookCurioItem(Item.Properties settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }
}
