package net.spell_engine.spellbinding.spellchoice;

import net.minecraft.item.ItemStack;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import org.jetbrains.annotations.Nullable;

public class SpellChoices {
    @Nullable
    public static SpellChoice from(ItemStack stack) {
        var choice = stack.get(SpellDataComponents.SPELL_CHOICE);
        return (choice != null && !choice.isEmpty()) ? choice : null;
    }
}