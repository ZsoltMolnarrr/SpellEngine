package net.spell_engine.compat.accessories;

import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

import java.util.function.Supplier;

/**
 * Universal spell book item for Accessories mod compatibility.
 * Pool ID is derived from the SPELL_CONTAINER component, not stored in the item.
 */
public class SpellBookAccessoryItem extends SpellHostAccessoryItem {
    public SpellBookAccessoryItem(Item.Settings settings, Supplier<RegistryEntry<SoundEvent>> equipSound) {
        super(settings, equipSound);
    }
}
