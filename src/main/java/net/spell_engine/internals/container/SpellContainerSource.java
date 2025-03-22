package net.spell_engine.internals.container;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpellContainerSource {
    public record Result(SpellContainer activeContainer, List<RegistryEntry<Spell>> actives, List<RegistryEntry<Spell>> passives, List<SpellContainerSource.SourcedContainer> sources) {
        public static final Result EMPTY = new Result(SpellContainer.EMPTY, List.of(), List.of(), List.of());
    }
    public interface Owner {
        Map<String, List<SourcedContainer>> spellContainerCache();
        void setSpellContainers(Result result);
        Result getSpellContainers();
    }
    public static SpellContainer activeContainerOf(PlayerEntity player) {
        return ((Owner)player).getSpellContainers().activeContainer;
    }
    public static List<RegistryEntry<Spell>> activeSpellsOf(PlayerEntity player) {
        return ((Owner)player).getSpellContainers().actives;
    }
    public static List<RegistryEntry<Spell>> passiveSpellsOf(PlayerEntity player) {
        return ((Owner)player).getSpellContainers().passives;
    }
    public static Result getSpellsOf(PlayerEntity player) {
        return ((Owner)player).getSpellContainers();
    }
    public static void setDirty(PlayerEntity player, Entry source) {
        setDirty(player, source.name());
    }
    public static void setDirty(PlayerEntity player, String source) {
        ((Owner)player).spellContainerCache().remove(source);
    }

    public record SourcedContainer(String name, ItemStack itemStack, SpellContainer container) { }
    public interface Source {
        List<SourcedContainer> getSpellContainers(PlayerEntity player, String name);
    }
    public interface DirtyChecker {
        Object current(PlayerEntity player);
    }
    public record Entry(String name, Source source, @Nullable DirtyChecker checker) { }
    public static final List<Entry> sources = new ArrayList<>();
    private static Entry entry(String name, Source source) {
        return entry(name, source, null);
    }
    private static Entry entry(String name, Source source, @Nullable DirtyChecker dirtyChecker) {
        var newEntry = new Entry(name, source, dirtyChecker);
        sources.add(newEntry);
        return newEntry;
    }
    public static final Entry MAIN_HAND = entry("main_hand", (player, sourceName) -> {
        var heldItemStack = player.getMainHandStack();
        var sources = new ArrayList<SourcedContainer>();
        addSourceIfValid(heldItemStack, sources, sourceName);
        return sources;
    });
    public static final Entry OFF_HAND = new Entry("off_hand", (player, sourceName) -> {
        var offhandStack = player.getOffHandStack();
        var sources = new ArrayList<SourcedContainer>();
        addSourceIfValid(offhandStack, sources, sourceName);
        return sources;
    }, player -> player.getOffHandStack());
    public static final Entry EQUIPMENT = new Entry("equipment", (player, sourceName) -> {
        var sources = new ArrayList<SourcedContainer>();
        if (SpellEngineMod.config.spell_container_from_equipment) {
            for (var slot : player.getInventory().armor) {
                addSourceIfValid(slot, sources, sourceName);
            }
        }
        return sources;
    }, player -> List.of(player.getInventory().armor.get(0), player.getInventory().armor.get(1),
            player.getInventory().armor.get(2), player.getInventory().armor.get(3))
    );
    private static void addSourceIfValid(ItemStack fromItemStack, List<SourcedContainer> sources, String name) {
        SpellContainer container = SpellContainerHelper.containerFromItemStack(fromItemStack);
        if (container != null && container.isValid()) {
            sources.add(new SpellContainerSource.SourcedContainer(name, fromItemStack, container));
        }
    }

    public static void addSource(Entry entry) {
        sources.add(entry);
    }
    public static void addSource(Entry entry, @Nullable String after) {
        boolean added = false;
        if (after != null) {
            // Index of the entry with the name `after`
            int index = -1;
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).name().equals(after)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                sources.add(index + 1, entry);
                added = true;
            }
        }
        if (!added) {
            sources.add(entry);
        }
    }

    public static void init() {
        if (SpellEngineMod.config.spell_container_from_offhand) {
            addSource(OFF_HAND);
        }
        if (SpellEngineMod.config.spell_container_from_equipment) {
            addSource(EQUIPMENT);
        }
    }

    public static void update(PlayerEntity player) {
        var owner = (Owner)player;
        var allContainers = new ArrayList<SourcedContainer>();
        boolean updated = false;
        if (SpellEngineMod.config.spell_container_caching) {
            for (var entry : sources) {
                if (owner.spellContainerCache().containsKey(entry.name())) {
                    allContainers.addAll(owner.spellContainerCache().get(entry.name()));
                } else {
                    // System.out.println("Container source dirty: " + entry.name() + " for " + player.getName());
                    var freshContainers = entry.source().getSpellContainers(player, entry.name());
                    allContainers.addAll(freshContainers);
                    owner.spellContainerCache().put(entry.name(), freshContainers);
                    updated = true;
                }
            }
        } else {
            for (var entry : sources) {
                var freshContainers = entry.source().getSpellContainers(player, entry.name());
                allContainers.addAll(freshContainers);
            }
            updated = true;
        }

        if (updated) {
            // System.out.println("Updating spell containers for " + player.getName());
            var heldItemStack = player.getMainHandStack();
            var heldContainer = SpellContainerHelper.containerFromItemStack(heldItemStack);
            var activeContainer = SpellContainer.EMPTY;
            List<RegistryEntry<Spell>> activeSpells = List.of();
            List<RegistryEntry<Spell>> passiveSpells = List.of();
            if (heldContainer != null && heldContainer.is_proxy()) {
                var merged = mergedContainerSources(allContainers, heldContainer.is_proxy(), heldContainer.content(), Spell.Type.ACTIVE, player.getWorld());
                activeContainer = merged.container();
                activeSpells = merged.spells();
            }
            var merged = mergedContainerSources(allContainers, false, null, Spell.Type.PASSIVE, player.getWorld());
            passiveSpells = merged.spells();

            ((Owner) player).setSpellContainers(new Result(activeContainer, activeSpells, passiveSpells, allContainers));
        }
    }

    public record MergeResult(SpellContainer container, List<RegistryEntry<Spell>> spells) {
        public static final MergeResult EMPTY = new MergeResult(SpellContainer.EMPTY, List.of());
    }

    public static List<RegistryEntry<Spell>> mergedContainerSources(List<SourcedContainer> sources, @Nullable SpellContainer.ContentType contentType, Spell.Type type, World world) {
        if (sources.isEmpty()) {
            return List.of();
        }
        var spells = new ArrayList<RegistryEntry<Spell>>();
        var registry = SpellRegistry.from(world);
        for (var source : sources) {
            var container = source.container();
            if (type == Spell.Type.ACTIVE && source.name.equals("off_hand")) {
                if (!SpellEngineMod.config.spell_container_from_offhand_any) {
                    if (!container.slotMatches(EquipmentSlot.OFFHAND.asString())) {
                        continue;
                    }
                }
            }
            if (container.contentMatches(contentType)) {
                for (var idString : container.spell_ids()) {
                    var id = Identifier.of(idString);
                    var spell = registry.getEntry(id).orElse(null);
                    if (spell != null && spell.value().type == type) {
                        spells.add(spell);
                    }
                }
            }
        }

        // Remove spells with the same group, and lower tier
        var toRemove = new HashSet<RegistryEntry<Spell>>();
        for (var spellEntry : spells) {
            var spell = spellEntry.value();
            var tag = spell.group;
            if (tag != null) {
                for (var other : spells) {
                    var spellId = spellEntry.getKey().get().getValue();
                    var otherId = other.getKey().get().getValue();
                    if (spellId.equals(otherId)) continue;
                    if (tag.equals(other.value().group)) {
                        if (spellEntry.value().tier == other.value().tier) {
                            if (spellEntry.value().sub_tier > other.value().sub_tier) {
                                toRemove.add(other);
                            }
                        }
                        if (spellEntry.value().tier > other.value().tier) {
                            toRemove.add(other);
                        }
                    }
                }
            }
        }
        spells.removeAll(toRemove);

        return spells;
//
//        var spellIds = new LinkedHashSet<String>(); // We need the IDs only, but remove duplicates
//        for (var spell : spells) {
//            spellIds.add(spell.getKey().get().getValue().toString());
//        }
//
//        // System.out.println("Updated for " + type + ", Spell IDs: " + spellIds);
//
//        var finalContentType = contentType != null ? contentType : SpellContainer.ContentType.MAGIC;
//        var container = new SpellContainer(finalContentType, proxy, null, 0, new ArrayList<>(spellIds));
//        return new MergeResult(container, spells);
    }

    public static MergeResult mergedContainerSources(List<SourcedContainer> sources, boolean proxy, @Nullable SpellContainer.ContentType contentType, Spell.Type type, World world) {
        if (sources.isEmpty()) {
            return MergeResult.EMPTY;
        }
        var spells = new ArrayList<RegistryEntry<Spell>>();
        var registry = SpellRegistry.from(world);
        for (var source : sources) {
            var container = source.container();
            if (type == Spell.Type.ACTIVE && source.name.equals("off_hand")) {
                if (!SpellEngineMod.config.spell_container_from_offhand_any) {
                    if (!container.slotMatches(EquipmentSlot.OFFHAND.asString())) {
                        continue;
                    }
                }
            }
            if (container.contentMatches(contentType)) {
                for (var idString : container.spell_ids()) {
                    var id = Identifier.of(idString);
                    var spell = registry.getEntry(id).orElse(null);
                    if (spell != null && spell.value().type == type) {
                        spells.add(spell);
                    }
                }
            }
        }

        // Remove spells with the same group, and lower tier
        var toRemove = new HashSet<RegistryEntry<Spell>>();
        for (var spellEntry : spells) {
            var spell = spellEntry.value();
            var tag = spell.group;
            if (tag != null) {
                for (var other : spells) {
                    var spellId = spellEntry.getKey().get().getValue();
                    var otherId = other.getKey().get().getValue();
                    if (spellId.equals(otherId)) continue;
                    if (tag.equals(other.value().group)) {
                        if (spellEntry.value().tier == other.value().tier) {
                            if (spellEntry.value().sub_tier > other.value().sub_tier) {
                                toRemove.add(other);
                            }
                        }
                        if (spellEntry.value().tier > other.value().tier) {
                            toRemove.add(other);
                        }
                    }
                }
            }
        }
        spells.removeAll(toRemove);

        var spellIds = new LinkedHashSet<String>(); // We need the IDs only, but remove duplicates
        for (var spell : spells) {
            spellIds.add(spell.getKey().get().getValue().toString());
        }

        // System.out.println("Updated for " + type + ", Spell IDs: " + spellIds);

        var finalContentType = contentType != null ? contentType : SpellContainer.ContentType.MAGIC;
        var container = new SpellContainer(finalContentType, proxy, null, 0, new ArrayList<>(spellIds));
        return new MergeResult(container, spells);
    }

    @Nullable public static SourcedContainer getFirstSourceOfSpell(Identifier spellId, PlayerEntity player) {
        var result = ((Owner)player).getSpellContainers();
        for (var source : result.sources()) {
            if (contains(source.container(), spellId)) {
                return source;
            }
        }
        return null;
    }
    private static boolean contains(SpellContainer container, Identifier spellId) {
        return container != null && container.spell_ids().contains(spellId.toString());
    }
}
