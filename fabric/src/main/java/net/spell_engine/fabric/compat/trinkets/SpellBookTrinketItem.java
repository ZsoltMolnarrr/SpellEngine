package net.spell_engine.fabric.compat.trinkets;

import net.minecraft.sounds.SoundEvent;

/**
 * Universal spell book item for Trinkets mod compatibility.
 * Pool ID is derived from the SPELL_CONTAINER component, not stored in the item.
 */
public class SpellBookTrinketItem extends SpellHostTrinketItem {
    public SpellBookTrinketItem(Properties settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }
}
