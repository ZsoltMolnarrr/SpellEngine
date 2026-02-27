package net.spell_engine.api.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

public class SpellEngineItemTags {
    /**
     * Items eligible for Spell Infinity enchantment.
     */
    public static final TagKey<Item> ENCHANTABLE_SPELL_INFINITY = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "enchantable/spell_infinity"));
    /**
     * Items those are considered spell books.
     * Allows equipping in the spell book slot.
     */
    public static final TagKey<Item> SPELL_BOOK = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "spell_books"));

    /**
     * Items that can be merged (placed) into spell books, to add new spells.
     * (Example: Spell Scroll)
     */
    public static final TagKey<Item> SPELL_BOOK_MERGEABLE = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "spell_book_mergeable"));

    /**
     * Items from which spells can be removed, using vanilla Grinding Stone
     * (Example: Spell Scroll)
     */
    public static final TagKey<Item> GRINDABLE = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "grindable"));

    public static final TagKey<Item> HANDHELD = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "handheld"));

    public static final TagKey<Item> NON_COMBAT_TOOLS = TagKey.of(Registries.ITEM.getKey(), Identifier.of(SpellEngineMod.ID, "non_combat_tools"));
}
