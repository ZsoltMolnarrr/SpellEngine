package net.spell_engine.api.spell.container;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_power.api.SpellSchool;

import java.util.*;
import java.util.stream.Collectors;

public class SpellContainerHelper {

    // Construction helpers for common use cases

    public static SpellContainer createForRangedWeapon() {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, List.of());
    }

    public static SpellContainer createForRangedWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, List.of(spellId));
    }

    public static SpellContainer createForRangedWeapon(List<Identifier> spellIds) {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, spellIds);
    }

    public static SpellContainer createForMagicWeapon() {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }

    public static SpellContainer createForMagicWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of(spellId));
    }

    public static SpellContainer createForMeleeWeapon() {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }

    public static SpellContainer createForMeleeWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of(spellId));
    }

    public static SpellContainer createForWeapon(SpellContainer.ContentType contentType, List<Identifier> spellIds) {
        var spellIdStrings = spellIds.stream().map(Identifier::toString).toList();
        return new SpellContainer(contentType, true, "", 0, spellIdStrings);
    }

    public static SpellContainer createForShield(Identifier spellId) {
        return createForShield(List.of(spellId));
    }

    public static SpellContainer createForShield(List<Identifier> spellIds) {
        return new SpellContainer(SpellContainer.ContentType.MAGIC, false, "", "offhand", 0, spellIds.stream().map(Identifier::toString).toList());
    }

    public static SpellContainer createForRelic(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.ANY, false, "", 0, List.of(spellId.toString()));
    }

    // Read helpers

    public static SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        var component = itemStack.get(SpellDataComponents.SPELL_CONTAINER);
        if (component != null) {
            return component;
        }
        var id = itemStack.getItem().getRegistryEntry().getKey().get().getValue();
        return SpellAssignments.containerForItem(id);
    }

    public static Identifier getPoolId(SpellContainer container) {
        if (container != null && container.pool() != null) {
            return Identifier.of(container.pool());
        }
        return null;
    }

    public static boolean contains(SpellContainer container, Identifier spellId) {
        return container != null && container.spell_ids().contains(spellId.toString());
    }

    // Misc helpers (Spell Binding)

    public static SpellContainer addSpell(World world, Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids());
        spellIds.add(spellId.toString());

        // Creating a map just for the sake of sorting
        HashMap<Identifier, Spell> spells = new HashMap<>();
        for (var idString : spellIds) {
            var id = Identifier.of(idString);
            var spellEntry = SpellRegistry.from(world).getEntry(id).orElse(null);
            if (spellEntry != null) {
                spells.put(id, spellEntry.value());
            }
        }
        var sortedSpellIds = spells.entrySet().stream()
                .sorted(SpellContainerHelper.spellSorter)
                .map(entry -> entry.getKey().toString())
                .collect(Collectors.toList());

        return container.copyWith(sortedSpellIds);
    }

    public static void addSpell(World world, Identifier spellId, ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isValid()) {
            System.err.println("Trying to add spell: " + spellId + " to an ItemStack without valid spell container");
            return;
        }
        var modifiedContainer = addSpell(world, spellId, container);
        itemStack.set(SpellDataComponents.SPELL_CONTAINER, modifiedContainer);
    }

    public static final Comparator<Map.Entry<Identifier, Spell>> spellSorter = (spell1, spell2) -> {
        if (spell1.getValue().tier > spell2.getValue().tier) {
            return 1;
        } else if (spell1.getValue().tier < spell2.getValue().tier) {
            return -1;
        } else {
            return spell1.getKey().toString().compareTo(spell2.getKey().toString());
        }
    };

    public static boolean hasValidContainer(ItemStack itemStack) {
        return containerFromItemStack(itemStack) != null;
    }

    public static boolean hasBindableContainer(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        return container != null && container.pool() != null && !container.pool().isEmpty();
    }

    public static boolean hasUsableContainer(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        return container != null && (container.isUsable() || container.is_proxy());
    }

    // Misc helpers (Scrolls)

    public static boolean isSpellValidForItem(Item item, RegistryEntry<Spell> spell) {
        var spellType = spell.value().school.archetype == SpellSchool.Archetype.ARCHERY
                ? SpellContainer.ContentType.ARCHERY : SpellContainer.ContentType.MAGIC;
        var expectedContentType = (item instanceof RangedWeaponItem) ? SpellContainer.ContentType.ARCHERY : SpellContainer.ContentType.MAGIC;
        return spellType == expectedContentType;
    }

    public static SpellContainer.ContentType contentTypeForItem(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container != null) {
            return container.content();
        }
        var item = itemStack.getItem();
        return (item instanceof RangedWeaponItem) ? SpellContainer.ContentType.ARCHERY : SpellContainer.ContentType.MAGIC;
    }

    public static SpellContainer.ContentType contentTypeForSpell(Spell spell) {
        return spell.school.archetype == SpellSchool.Archetype.ARCHERY
                ? SpellContainer.ContentType.ARCHERY : SpellContainer.ContentType.MAGIC;
    }

    public static SpellContainer create(RegistryEntry<Spell> spell, Item item) {
        return create(List.of(spell), item);
    }

    public static SpellContainer create(List<RegistryEntry<Spell>> spells, Item item) {
        final var contentType = contentTypeForSpell(spells.get(0).value());
        var isProxy = !(ISpellBookItem.isSpellBook(item) || item.getRegistryEntry().isIn(SpellEngineItemTags.SPELL_BOOK_MERGEABLE));
        var spellIds = spells.stream()
                .filter(entry -> contentTypeForSpell(entry.value()) == contentType)
                .map(entry -> entry.getKey().get().getValue().toString())
                .toList();
        return new SpellContainer(contentType, isProxy, "", spellIds.size(), spellIds);
    }
}