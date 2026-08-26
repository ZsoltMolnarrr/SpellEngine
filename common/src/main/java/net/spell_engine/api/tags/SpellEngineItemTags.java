package net.spell_engine.api.tags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.spell_engine.SpellEngineMod;

public class SpellEngineItemTags {
    /**
     * Items eligible for Spell Infinity enchantment.
     */
    public static final TagKey<Item> ENCHANTABLE_SPELL_INFINITY = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "enchantable/spell_infinity"));
    /**
     * Items those are considered spell books.
     * Allows equipping in the spell book slot.
     */
    public static final TagKey<Item> SPELL_BOOK = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_books"));

    /**
     * Items that can be merged (placed) into spell books, to add new spells.
     * (Example: Spell Scroll)
     */
    public static final TagKey<Item> SPELL_BOOK_MERGEABLE = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_book_mergeable"));

    /**
     * Items from which spells can be removed, using vanilla Grinding Stone
     * (Example: Spell Scroll)
     */
    public static final TagKey<Item> GRINDABLE = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "grindable"));

    public static final TagKey<Item> HANDHELD = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "handheld"));

    public static final TagKey<Item> NON_COMBAT_TOOLS = TagKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "non_combat_tools"));
}
