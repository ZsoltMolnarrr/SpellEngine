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

    /// How the spells of a single container are ordered: by quality alone, since they are read as a
    /// hotbar, where what a spell costs its wielder to reach matters more than where it came from.
    public static final Comparator<Map.Entry<Identifier, Spell>> spellSorter = Comparator
            .comparingInt((Map.Entry<Identifier, Spell> entry) -> entry.getValue().tier)
            .thenComparingInt(entry -> entry.getValue().sub_tier)
            .thenComparing(entry -> entry.getKey().toString());

    /// How spells are ordered when a catalog of them is browsed - the spell binding table, the creative
    /// menu. Unlike a container, a catalog holds the spells of every mod at once, so it is laid out like a
    /// library: by mod, then by tier, and only within a tier by group. Tier leads group because the spell
    /// binding table lays each tier out as its own row - the catalog is sorted flat and chunked into rows
    /// by tier, so tier-adjacent spells must stay contiguous, with `group` merely ordering them inside a row.
    public static int compareInCatalog(Identifier id1, Spell spell1, Identifier id2, Spell spell2) {
        var byNamespace = id1.getNamespace().compareTo(id2.getNamespace());
        if (byNamespace != 0) {
            return byNamespace;
        }
        var byTier = Integer.compare(spell1.tier, spell2.tier);
        if (byTier != 0) {
            return byTier;
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

    public static final Comparator<Map.Entry<Identifier, Spell>> catalogSorter = (entry1, entry2) ->
            compareInCatalog(entry1.getKey(), entry1.getValue(), entry2.getKey(), entry2.getValue());

    public static final Comparator<RegistryEntry<Spell>> catalogEntrySorter = (entry1, entry2) ->
            compareInCatalog(entry1.getKey().get().getValue(), entry1.value(),
                    entry2.getKey().get().getValue(), entry2.value());

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
}