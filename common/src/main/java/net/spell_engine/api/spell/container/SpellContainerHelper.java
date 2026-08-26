package net.spell_engine.api.spell.container;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.*;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellAssignments;

import java.util.*;
import java.util.stream.Collectors;

public class SpellContainerHelper {

    // Read helpers

    public static SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        var component = itemStack.get(SpellDataComponents.SPELL_CONTAINER);
        if (component != null) {
            return component;
        }
        var id = itemStack.getItem().builtInRegistryHolder().unwrapKey().get().identifier();
        return SpellAssignments.containerForItem(id);
    }

    public static Identifier getPoolId(SpellContainer container) {
        if (container != null && container.pool() != null) {
            return Identifier.parse(container.pool());
        }
        return null;
    }

    public static boolean contains(SpellContainer container, Identifier spellId) {
        return container != null && container.spell_ids().contains(spellId.toString());
    }

    // Misc helpers (Spell Binding)

    public static List<String> sortedSpells(Level world, List<String> spellIds) {
        HashMap<Identifier, Spell> spells = new HashMap<>();
        for (var idString : spellIds) {
            var id = Identifier.parse(idString);
            var spellEntry = SpellRegistry.from(world).get(id).orElse(null);
            if (spellEntry != null) {
                spells.put(id, spellEntry.value());
            }
        }
        return spells.entrySet().stream()
                .sorted(SpellContainerHelper.containerSorter)
                .map(entry -> entry.getKey().toString())
                .collect(Collectors.toList());
    }

    public static SpellContainer addSpell(Level world, Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids());
        spellIds.add(spellId.toString());
        return container.copyWith(sortedSpells(world, spellIds));
    }

    public static void addSpell(Level world, Identifier spellId, ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isValid()) {
            System.err.println("Trying to add spell: " + spellId + " to an ItemStack without valid spell container");
            return;
        }
        var modifiedContainer = addSpell(world, spellId, container);
        itemStack.set(SpellDataComponents.SPELL_CONTAINER, modifiedContainer);
    }

    public static SpellContainer removeSpell(Level world, Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids());
        spellIds.remove(spellId.toString());
        return container.copyWith(sortedSpells(world, spellIds));
    }

    public static void removeSpell(Level world, Identifier spellId, ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isValid()) {
            System.err.println("Trying to remove spell: " + spellId + " from an ItemStack without valid spell container");
            return;
        }
        var modifiedContainer = removeSpell(world, spellId, container);
        itemStack.set(SpellDataComponents.SPELL_CONTAINER, modifiedContainer);
    }

    // Sorting
    //
    // Two regimes, deliberately different. A *container* is read like a hotbar (one wielder, quality is all
    // that matters), a *catalog* is browsed like a library (every mod's spells at once, laid out by origin).

    /// How the spells of a single container are ordered: by quality alone, since they are read as a
    /// hotbar, where what a spell costs its wielder to reach matters more than where it came from.
    public static final Comparator<Map.Entry<Identifier, Spell>> containerSorter = Comparator
            .comparingInt((Map.Entry<Identifier, Spell> entry) -> entry.getValue().tier)
            .thenComparingInt(entry -> entry.getValue().sub_tier)
            .thenComparing(entry -> entry.getKey().toString());

    /// How spells are ordered when a catalog of them is browsed - the spell binding table, the creative
    /// menu. Unlike a container, a catalog holds the spells of every mod at once, but it is still laid out
    /// tier-first: the binding table lays each tier out as its own row, so the catalog is sorted flat and
    /// chunked into rows by tier, and tier-adjacent spells must stay contiguous no matter which mod they
    /// come from. Only within a tier does mod (namespace) group spells together, and `group` order them
    /// inside a row. Without tier leading, a high-tier spell from a namespace that sorts early (e.g. a
    /// tier-5 spell) would jump into the first row instead of the last.
    public static int compareInCatalog(Identifier id1, Spell spell1, Identifier id2, Spell spell2) {
        var byTier = Integer.compare(spell1.tier, spell2.tier);
        if (byTier != 0) {
            return byTier;
        }
        var byNamespace = id1.getNamespace().compareTo(id2.getNamespace());
        if (byNamespace != 0) {
            return byNamespace;
        }
        var byGroup = groupOf(spell1).compareTo(groupOf(spell2));
        if (byGroup != 0) {
            return byGroup;
        }
        var bySubTier = Integer.compare(spell1.sub_tier, spell2.sub_tier);
        if (bySubTier != 0) {
            return bySubTier;
        }
        return id1.getPath().compareTo(id2.getPath());
    }

    /// Defaults to empty, but JSON is free to spell the group out as `null`, which no ordering survives.
    private static String groupOf(Spell spell) {
        return spell.group != null ? spell.group : "";
    }

    /// `compareInCatalog` over map entries, for streams keyed by spell id.
    public static final Comparator<Map.Entry<Identifier, Spell>> catalogSorter = (entry1, entry2) ->
            compareInCatalog(entry1.getKey(), entry1.getValue(), entry2.getKey(), entry2.getValue());

    /// `compareInCatalog` over registry entries, for streams straight off the spell registry.
    public static final Comparator<Holder<Spell>> catalogEntrySorter = (entry1, entry2) ->
            compareInCatalog(entry1.unwrapKey().get().identifier(), entry1.value(),
                    entry2.unwrapKey().get().identifier(), entry2.value());

    // Container queries

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

    public static int poolTierSize(List<Holder<Spell>> spells) {
        var tiers = new HashSet<Integer>();
        for (var spellEntry : spells) {
            var spell = spellEntry.value();
            tiers.add(spell.tier);
        }
        return tiers.size();
    }
}