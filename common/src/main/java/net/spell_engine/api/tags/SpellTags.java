package net.spell_engine.api.tags;

import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;

public class SpellTags {
    private static TagKey<Spell> tag(String name) {
        return TagKey.of(SpellRegistry.KEY, Identifier.of(SpellEngineMod.ID, name));
    }

    public static final String SPELL_SCROLL_PREFIX = "spell_scroll/";
    public static TagKey<Spell> spellScroll(String namespace, String path) {
        return TagKey.of(SpellRegistry.KEY, Identifier.of(namespace, SPELL_SCROLL_PREFIX + path));
    }
    public static final String SPELL_BOOK_PREFIX = "spell_book/";
    public static TagKey<Spell> spellBook(String namespace, String path) {
        return TagKey.of(SpellRegistry.KEY, Identifier.of(namespace, SPELL_BOOK_PREFIX + path));
    }
    public static final String WEAPON_PREFIX = "weapon/";
    public static TagKey<Spell> weapon(String namespace, String path) {
        return TagKey.of(SpellRegistry.KEY, Identifier.of(namespace, WEAPON_PREFIX + path));
    }

    /**
     * Spells that can be found in loot chests applied onto spell scrolls.
     */
    public static final TagKey<Spell> TREASURE = tag("treasure");
}
