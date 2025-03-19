package net.spell_engine.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScrollItem extends Item {
    public static final Identifier ID = Identifier.of("spell_engine", "scroll");

    public ScrollItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Nullable public static boolean applySpell(ItemStack itemStack, RegistryEntry<Spell> spellEntry, boolean requirePool) {
        if (spellEntry.isIn(SpellTags.TREASURE)) {
            itemStack.set(SpellDataComponents.SPELL_CONTAINER, SpellContainerHelper.create(spellEntry, itemStack.getItem()));
            setRarity(itemStack, spellEntry);
            return true;
        } else {
            return false;
        }
    }

    public static void setRarity(ItemStack itemStack, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        var ordinal = Math.max(spell.tier - 1, 0); // minimum 0
        var rarity = Rarity.values().length > ordinal ? Rarity.values()[ordinal] : Rarity.EPIC;
        itemStack.set(DataComponentTypes.RARITY, rarity);
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Text
                    .translatable("item.spell_engine.scroll.table_hint")
                    .formatted(Formatting.GRAY)
            );
        }
    }
}
