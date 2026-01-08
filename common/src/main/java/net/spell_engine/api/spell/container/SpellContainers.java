package net.spell_engine.api.spell.container;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public class SpellContainers {

    // Construction helpers for common use cases

    public static SpellContainer forRangedWeapon() {
        return forWeapon(SpellContainer.ContentType.ARCHERY, List.of());
    }

    public static SpellContainer forMagicWeapon() {
        return forWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }

    public static SpellContainer forMeleeWeapon() {
        return forWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }

    public static SpellContainer forWeapon(SpellContainer.ContentType contentType, List<Identifier> spellIds) {
        var spellIdStrings = spellIds.stream().map(Identifier::toString).toList();
        return new SpellContainer(contentType, "", "", 0, spellIdStrings);
    }

    public static SpellContainer forShield(Identifier spellId) {
        return forShield(List.of(spellId));
    }

    public static SpellContainer forShield(List<Identifier> spellIds) {
        return new SpellContainer(SpellContainer.ContentType.MAGIC, "", "", "offhand", 0, spellIds.stream().map(Identifier::toString).toList());
    }

    public static SpellContainer forRelic(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.NONE, "", "", 0, List.of(spellId.toString()));
    }

    public static SpellContainer forModifier(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.NONE, "", "", 0, List.of(spellId.toString()));
    }

    public static SpellContainer forScroll(RegistryEntry<Spell> spellEntry) {
        return new SpellContainer(SpellContainer.ContentType.NONE, "", "", 0, List.of(spellEntry.getKey().get().getValue().toString()));
    }
}