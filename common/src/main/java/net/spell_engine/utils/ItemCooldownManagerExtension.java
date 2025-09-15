package net.spell_engine.utils;

import net.minecraft.item.Item;

public interface ItemCooldownManagerExtension {
    int SE_getLastCooldownDuration(Item item);
}
