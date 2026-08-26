package net.spell_engine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.client.SpellEngineClient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScrollItem extends Item {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_scroll");

    public ScrollItem(Properties settings) {
        super(settings);
    }


    public static void applySpell(ItemStack itemStack, Holder<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        itemStack.set(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forScroll(spellEntry));
        onSpellAdded(itemStack, spellEntry, pool);
    }

    public static String translationKeyForPool(Identifier poolId) {
        return "item." + poolId.getNamespace() + "." + poolId.getPath();
    }

    /// 1.21.11: the `minecraft:item_model` component names an item-model definition, resolved at
    /// `assets/<ns>/items/<path>.json`, so the pool id maps 1:1 (e.g. `paladins:spell_scroll/paladin`
    /// → `assets/paladins/items/spell_scroll/paladin.json`).
    public static Identifier modelIdForPool(Identifier poolId) {
        return poolId;
    }

    public static void onSpellAdded(ItemStack itemStack, Holder<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        // Set rarity
        var spell = spellEntry.value();
        var ordinal = Math.max(spell.tier - 1, 0); // minimum 0
        var rarity = Rarity.values().length > ordinal ? Rarity.values()[ordinal] : Rarity.EPIC;
        itemStack.set(DataComponents.RARITY, rarity);

        if (pool != null) {
            // Set custom model override
            var modelId = modelIdForPool(pool.location());
            itemStack.set(DataComponents.ITEM_MODEL, modelId); // item-model definition: assets/<ns>/items/<pool path>.json

            // Set custom name
            // - Example: "paladins:spell_scroll/paladin" -> "item.paladins.paladin_spell_scroll"
            var key = translationKeyForPool(pool.location());
            if (Language.getInstance().has(key)) {
                itemStack.set(DataComponents.ITEM_NAME, Component.translatable(key));
            }
        }
    }

    @Nullable public static TagKey<Spell> resolveSpellPool(Level world, Holder<Spell> spellEntry) {
        var registry = world.registryAccess().lookup(SpellRegistry.KEY);
        if (registry.isPresent()) {
            return resolveSpellPool(registry.get(), spellEntry);
        } else {
            return null;
        }
    }

    @Nullable public static TagKey<Spell> resolveSpellPool(Registry<Spell> wrapper, Holder<Spell> spellEntry) {
        // Find the first tag in which spellEntry is contained
        var tag = wrapper.getTags()
                .filter(t ->
                        t.key().location().getPath().startsWith(SpellTags.SPELL_SCROLL_PREFIX)
                                && t.contains(spellEntry)
                )
                .findFirst();
        if (tag.isPresent()) {
            return tag.get().unwrapKey().get();
        } else {
            return null;
        }
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Component
                    .translatable("item.spell_engine.scroll.table_hint")
                    .withStyle(ChatFormatting.GRAY)
            );
        }
    }
}
