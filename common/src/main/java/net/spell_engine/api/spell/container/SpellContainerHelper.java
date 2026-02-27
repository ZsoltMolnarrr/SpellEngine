package net.spell_engine.api.spell.container;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellAssignments;

import java.util.*;
import java.util.stream.Collectors;

public class SpellContainerHelper {

    // Construction helpers for common use cases
    @Deprecated(forRemoval = true)
    public static SpellContainer createForRangedWeapon() {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, List.of());
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForRangedWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, List.of(spellId));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForRangedWeapon(List<Identifier> spellIds) {
        return createForWeapon(SpellContainer.ContentType.ARCHERY, spellIds);
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForMagicWeapon() {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForMagicWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of(spellId));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForMeleeWeapon() {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of());
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForMeleeWeapon(Identifier spellId) {
        return createForWeapon(SpellContainer.ContentType.MAGIC, List.of(spellId));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForWeapon(SpellContainer.ContentType contentType, List<Identifier> spellIds) {
        var spellIdStrings = spellIds.stream().map(Identifier::toString).toList();
        return new SpellContainer(contentType, "", "", 0, spellIdStrings);
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForShield(Identifier spellId) {
        return createForShield(List.of(spellId));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForShield(List<Identifier> spellIds) {
        return new SpellContainer(SpellContainer.ContentType.MAGIC, "", "", "offhand", 0, spellIds.stream().map(Identifier::toString).toList());
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForRelic(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.ANY, "", "", 0, List.of(spellId.toString()));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForSpellHost(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.MAGIC, "", "", 0, List.of(spellId.toString()));
    }
    @Deprecated(forRemoval = true)
    public static SpellContainer createForModifier(Identifier spellId) {
        return new SpellContainer(SpellContainer.ContentType.ANY, "", "", 0, List.of(spellId.toString()));
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

    public static List<String> sortedSpells(World world, List<String> spellIds) {
        HashMap<Identifier, Spell> spells = new HashMap<>();
        for (var idString : spellIds) {
            var id = Identifier.of(idString);
            var spellEntry = SpellRegistry.from(world).getEntry(id).orElse(null);
            if (spellEntry != null) {
                spells.put(id, spellEntry.value());
            }
        }
        return spells.entrySet().stream()
                .sorted(SpellContainerHelper.spellSorter)
                .map(entry -> entry.getKey().toString())
                .collect(Collectors.toList());
    }

    public static SpellContainer addSpell(World world, Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids());
        spellIds.add(spellId.toString());
        return container.copyWith(sortedSpells(world, spellIds));
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

    public static SpellContainer removeSpell(World world, Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids());
        spellIds.remove(spellId.toString());
        return container.copyWith(sortedSpells(world, spellIds));
    }

    public static void removeSpell(World world, Identifier spellId, ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isValid()) {
            System.err.println("Trying to remove spell: " + spellId + " from an ItemStack without valid spell container");
            return;
        }
        var modifiedContainer = removeSpell(world, spellId, container);
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
        return container != null && (container.isUsable() || container.isResolver());
    }

    public static int poolTierSize(List<RegistryEntry<Spell>> spells) {
        var tiers = new HashSet<Integer>();
        for (var spellEntry : spells) {
            var spell = spellEntry.value();
            tiers.add(spell.tier);
        }
        return tiers.size();
    }

//    public static SpellContainer create(RegistryEntry<Spell> spell, Item item) {
//        return create(List.of(spell), item);
//    }
//
//    public static SpellContainer create(List<RegistryEntry<Spell>> spells, Item item) {
//        final var contentType = contentTypeForSpell(spells.get(0).value());
//        var isProxy = !(ISpellBookItem.isSpellBook(item) || item.getRegistryEntry().isIn(SpellEngineItemTags.SPELL_BOOK_MERGEABLE));
//        var spellIds = spells.stream()
//                .filter(entry -> contentTypeForSpell(entry.value()) == contentType)
//                .map(entry -> entry.getKey().get().getValue().toString())
//                .toList();
//        return new SpellContainer(contentType, isProxy, "", spellIds.size(), spellIds);
//    }
}