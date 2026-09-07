package net.spell_engine.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.client.SpellEngineClient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScrollItem extends Item {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_scroll");

    public ScrollItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    // Name and rarity overrides are applied stack-side by `ItemStackNameMixin`, not here: with Trinkets /
    // Curios present the registered scroll is a slot-mod subclass, not this class.

    public static void applySpell(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        SpellItemData.setSpellContainer(itemStack, SpellContainers.forScroll(spellEntry));
        onSpellAdded(itemStack, spellEntry, pool);
    }

    public static String translationKeyForPool(Identifier poolId) {
        return "item." + poolId.getNamespace() + "." + poolId.getPath();
    }

    public static Identifier modelIdForPool(Identifier poolId) {
        return new Identifier(poolId.getNamespace(), "item/" + poolId.getPath());
    }

    public static void onSpellAdded(ItemStack itemStack, RegistryEntry<Spell> spellEntry, @Nullable TagKey<Spell> pool) {
        // Set rarity
        var spell = spellEntry.value();
        var ordinal = Math.max(spell.tier - 1, 0); // minimum 0
        var rarity = Rarity.values().length > ordinal ? Rarity.values()[ordinal] : Rarity.EPIC;
        SpellItemData.setRarity(itemStack, rarity);

        if (pool != null) {
            // Set custom model override
            var modelId = modelIdForPool(pool.id());
            SpellItemData.setItemModel(itemStack, modelId);

            // Set custom name
            // - Example: "paladins:spell_scroll/paladin" -> "item.paladins.spell_scroll/paladin"
            // Written unconditionally; whether the key resolves is checked at display time in
            // `ItemStackNameMixin` (this also runs server-side, where client translations are not visible).
            SpellItemData.setItemNameKey(itemStack, translationKeyForPool(pool.id()));
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

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Text
                    .translatable("item.spell_engine.scroll.table_hint")
                    .formatted(Formatting.GRAY)
            );
        }
    }
}
