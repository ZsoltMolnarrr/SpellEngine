package net.spell_engine.internals;

import net.minecraft.item.ItemStack;
import net.spell_engine.api.spell.SpellContainer;

public class SpellContainerHelper {
    public static SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        var object = (Object)itemStack;
        if (object instanceof SpellCasterItemStack stack) {
            var container = stack.getSpellContainer();
            if (container != null && container.isValid()) {
                return container;
            }
        }
        return null;
    }
}
