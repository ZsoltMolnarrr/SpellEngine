package net.spell_engine.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.client.SpellEngineClient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScrollItem extends Item {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "spell_scroll");

    public ScrollItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    public static void applySpell(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        itemStack.set(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forScroll(spellEntry));
        onSpellAdded(itemStack, spellEntry, pool);
    }

    public static String translationKeyForPool(Identifier poolId) {
        return "item." + poolId.getNamespace() + "." + poolId.getPath();
    }

    public static Identifier modelIdForPool(Identifier poolId) {
        return Identifier.of(poolId.getNamespace(), "item/" + poolId.getPath());
    }

    public static void onSpellAdded(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        // Set rarity
        var spell = spellEntry.value();
        var ordinal = Math.max(spell.tier - 1, 0); // minimum 0
        var rarity = Rarity.values().length > ordinal ? Rarity.values()[ordinal] : Rarity.EPIC;
        itemStack.set(DataComponentTypes.RARITY, rarity);

        if (pool != null) {
            // Set custom model override
            var modelId = modelIdForPool(pool.id());
            itemStack.set(SpellDataComponents.ITEM_MODEL, modelId);

            // Set custom name
            // - Example: "paladins:spell_scroll/paladin" -> "item.paladins.paladin_spell_scroll"
            var key = translationKeyForPool(pool.id());
            if (Language.getInstance().hasTranslation(key)) {
                itemStack.set(DataComponentTypes.ITEM_NAME, Text.translatable(key));
            }
        }
    }

    @Nullable public static TagKey<Spell> resolveSpellPool(World world, RegistryEntry<Spell> spellEntry) {
        var wrapper = world.getRegistryManager().getOptionalWrapper(SpellRegistry.KEY);
        if (wrapper.isPresent()) {
            return resolveSpellPool(wrapper.get(), spellEntry);
        } else {
            return null;
        }
    }

    @Nullable public static TagKey<Spell> resolveSpellPool(RegistryWrapper<Spell> wrapper, RegistryEntry<Spell> spellEntry) {
        // Find the first tag in which spellEntry is contained
        var tag = wrapper.streamTags()
                .filter(t ->
                        t.getTagKey().get().id().getPath().startsWith(SpellTags.SPELL_SCROLL_PREFIX)
                                && t.contains(spellEntry)
                )
                .findFirst();
        if (tag.isPresent()) {
            return tag.get().getTagKey().get();
        } else {
            return null;
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
