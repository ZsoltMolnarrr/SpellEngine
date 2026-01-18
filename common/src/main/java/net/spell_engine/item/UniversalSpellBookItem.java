package net.spell_engine.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Rarity;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerTemplates;
import net.spell_engine.api.tags.SpellTags;

/**
 * A universal spell book item that derives its configuration from data components.
 * Variants are created by applying different spell tags to the same item.
 */
public class UniversalSpellBookItem extends Item {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "spell_book");

    public UniversalSpellBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    /**
     * Apply spell book configuration to an ItemStack based on a tag.
     * @param itemStack The stack to configure
     * @param tag The spell tag (e.g., wizards:spell_book/fire)
     * @return true if configuration was successful
     */
    public static boolean applyFromTag(ItemStack itemStack, TagKey<Spell> tag) {
        var tagPath = tag.id().getPath();
        if (!tagPath.startsWith(SpellTags.SPELL_BOOK_PREFIX)) {
            return false;
        }

        // Pool ID is the same as the tag ID (e.g., wizards:spell_book/fire)
        var poolId = tag.id();

        // Create spell container with binding pool
        var config = SpellContainerTemplates.config.safeValue();
        var baseContainer = config.spell_book != null
                ? config.spell_book
                : SpellContainerTemplates.defaults().spell_book;
        var container = baseContainer.withBindingPool(poolId);

        itemStack.set(SpellDataComponents.SPELL_CONTAINER, container);
        onConfigured(itemStack, tag);
        return true;
    }

    /**
     * Called after spell container is applied. Sets model, name, rarity.
     */
    private static void onConfigured(ItemStack itemStack, TagKey<Spell> tag) {
        // Set rarity (could be enhanced later based on pool configuration)
        // itemStack.set(DataComponentTypes.RARITY, Rarity.UNCOMMON);

        // Set custom model ID
        var modelId = modelIdForPool(tag.id());
        itemStack.set(SpellDataComponents.ITEM_MODEL, modelId);

        // Set custom name if translation exists
        var key = translationKeyForPool(tag.id());
        if (Language.getInstance().hasTranslation(key)) {
            itemStack.set(DataComponentTypes.ITEM_NAME, Text.translatable(key));
        }
    }

    /**
     * Generate model ID for this spell book variant.
     * Tag: wizards:spell_book/fire -> Model: wizards:item/fire_spell_book
     */
    public static Identifier modelIdForPool(Identifier tagId) {
        return Identifier.of(tagId.getNamespace(), "item/" + tagId.getPath());
    }

    /**
     * Generate translation key for this spell book variant.
     * Tag: wizards:spell_book/fire -> Key: item.wizards.spell_book/fire
     */
    public static String translationKeyForPool(Identifier poolId) {
        return "item." + poolId.getNamespace() + "." + poolId.getPath();
    }

    /**
     * Get the description translation key from an ItemStack's spell container pool.
     * Returns the translation key with ".spell_binding.description" suffix.
     * @param itemStack The spell book ItemStack
     * @return The description translation key, or null if no pool is set
     */
    public static String descriptionKeyFromStack(ItemStack itemStack) {
        var container = itemStack.get(SpellDataComponents.SPELL_CONTAINER);
        if (container == null || container.pool() == null || container.pool().isEmpty()) {
            return null;
        }
        var poolId = Identifier.of(container.pool());
        return translationKeyForPool(poolId) + ".spell_binding.description";
    }
}
