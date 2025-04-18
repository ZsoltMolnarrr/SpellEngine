package net.spell_engine.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Rarity;
import net.spell_engine.api.spell.SpellTagsNumbered;
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

    @Nullable public static boolean applySpell(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        if (spellEntry.isIn(SpellTags.TREASURE)) {
            itemStack.set(SpellDataComponents.SPELL_CONTAINER, SpellContainerHelper.create(spellEntry, itemStack.getItem()));
            onSpellAdded(itemStack, spellEntry, pool);
            return true;
        } else {
            return false;
        }
    }

    public static void onSpellAdded(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        // Set rarity
        var spell = spellEntry.value();
        var ordinal = Math.max(spell.tier - 1, 0); // minimum 0
        var rarity = Rarity.values().length > ordinal ? Rarity.values()[ordinal] : Rarity.EPIC;
        itemStack.set(DataComponentTypes.RARITY, rarity);

        if (pool != null) {
            // Set custom model data
            var number = SpellTagsNumbered.get(pool.id());
            if (number != SpellTagsNumbered.NONE) {
                itemStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(number));
            }

            // Set custom name
            // - Example: "item.paladins.paladin.spell_scroll"
            var key = "item." + pool.id().getNamespace() + "." + pool.id().getPath() + ".spell_scroll";
            Language.getInstance().hasTranslation(key);
            var name = Text.translatable(key);
            itemStack.set(DataComponentTypes.ITEM_NAME, name);
        }
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
