package net.spell_engine.spellbinding.spellchoice;

import net.minecraft.item.ItemStack;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import org.jetbrains.annotations.Nullable;

public class SpellChoices {
    /// The pending spell choice of the stack (NBT, else the datapack assignment, else the item-level default
    /// - see `SpellContainerHelper#choiceFromItemStack`), `null` when there is none or it has been resolved
    @Nullable
    public static SpellChoice from(ItemStack stack) {
        var choice = SpellContainerHelper.choiceFromItemStack(stack);
        return (choice != null && !choice.isEmpty()) ? choice : null;
    }

    /// Resolves the spell choice of the stack: an empty choice is stored on the stack, which shadows both the
    /// datapack assignment and the item-level default (plainly removing the NBT would make them reappear).
    public static void clear(ItemStack stack) {
        SpellItemData.setSpellChoice(stack, SpellChoice.EMPTY);
    }
}
