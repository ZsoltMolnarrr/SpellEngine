package net.spell_engine.utils;

import net.minecraft.world.item.ItemStack;

public interface ItemCooldownManagerExtension {
    /// Last cooldown duration set for the cooldown group of the given stack (0 when unknown)
    int SE_getLastCooldownDuration(ItemStack stack);
}
