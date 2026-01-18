package net.spell_engine.internals.container;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpellContainerSource {
    public record Result(
            SpellContainer activeContainer,
            List<RegistryEntry<Spell>> actives,
            List<RegistryEntry<Spell>> passives,
            List<RegistryEntry<Spell>> modifiers,
            List<SpellContainerSource.SourcedContainer> sources) {
        public static final Result EMPTY = new Result(SpellContainer.EMPTY, List.of(), List.of(), List.of(), List.of());
    }
    public interface Owner {
        Map<String, List<SourcedContainer>> spellContainerCache();
        Map<Identifier, List<Spell.Modifier>> spellModifierCache();
        LinkedHashMap<String, SpellContainer> serverSideSpellContainers();
        void markServerSideSpellContainersDirty();
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
    public static void setDirty(PlayerEntity player, ItemEntry source) {
        setDirty(player, source.name());
    }
    public static void setDirty(PlayerEntity player, String source) {
        ((Owner)player).spellContainerCache().remove(source);
    }
    public static void setDirtyServerSide(PlayerEntity player) {
        ((Owner)player).markServerSideSpellContainersDirty();
    }
    public static void syncServerSideContainers(PlayerEntity player) {
        if (!player.getWorld().isClient) {
            var containers = ((Owner)player).serverSideSpellContainers();
            var packet = new Packets.SpellContainerSync(containers);
            ServerPlayNetworking.send((ServerPlayerEntity) player, packet);
            setDirty(player, MAIN_HAND);
        }
    }

    public interface DirtyChecker {
        Object current(PlayerEntity player);
    }
    public record SourcedContainer(String name, @Nullable ItemStack itemStack, SpellContainer container) { }
    public interface Source {
        List<SourcedContainer> getSpellContainers(PlayerEntity player, String name);
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

    public interface ItemStackSource extends Source {
        List<ItemStack> getSpellContainerItemStacks(PlayerEntity player, String name);
        @Override
        default List<SourcedContainer> getSpellContainers(PlayerEntity player, String name) {
            var itemStacks = getSpellContainerItemStacks(player, name);
            var sources = new ArrayList<SourcedContainer>();
            for (var itemStack : itemStacks) {
                SpellContainer container = SpellContainerHelper.containerFromItemStack(itemStack);
                if (container != null && container.isValid()) {
                    sources.add(new SpellContainerSource.SourcedContainer(name, itemStack, container));
                }
            }
            return sources;
        }
    }
    public record ItemEntry(String name, ItemStackSource source, @Nullable DirtyChecker checker) {
        public static ItemEntry of(String name, ItemStackSource source, @Nullable DirtyChecker dirtyChecker) {
            return new ItemEntry(name, source, dirtyChecker);
        }
        public static ItemEntry of(String name, ItemStackSource source)  {
            return of(name, source, player -> source.getSpellContainerItemStacks(player, name));
        }
    }
    public static final List<ItemEntry> itemSources = new ArrayList<>();
    private static ItemEntry itemEntry(String name, ItemStackSource source) {
        var newEntry = ItemEntry.of(name, source);
        addItemSource(newEntry);
        return newEntry;
    }

//    private static void addSourceIfValid(ItemStack fromItemStack, List<SourcedContainer> sources, String name) {
//        SpellContainer container = SpellContainerHelper.containerFromItemStack(fromItemStack);
//        if (container != null && container.isValid()) {
//            sources.add(new SpellContainerSource.SourcedContainer(name, fromItemStack, container));
//        }
//    }

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
    public static void addItemSource(ItemEntry entry) {
        addItemSource(entry, null);
    }
    public static void addItemSource(ItemEntry entry, @Nullable String after) {
        itemSources.add(entry);
        addSource(new Entry(entry.name(), entry.source(), entry.checker()), after);
    }

    public static final ItemEntry MAIN_HAND = itemEntry("main_hand", (player, sourceName) -> {
        return List.of(player.getMainHandStack());
    });
    public static final ItemEntry OFF_HAND = itemEntry("off_hand", (player, sourceName) -> {
        return List.of(player.getOffHandStack());
    });
    public static final ItemEntry ARMOR = itemEntry("armor", (player, sourceName) -> {
        return List.of(player.getInventory().armor.get(0), player.getInventory().armor.get(1),
                player.getInventory().armor.get(2), player.getInventory().armor.get(3));
    });

    public static void init() {
    }

    public static void update(PlayerEntity player) {
        var owner = (Owner)player;
        var allContainers = new ArrayList<SourcedContainer>();
        boolean updated = false;

        if (SpellEngineMod.config.spell_container_caching) {
            for (var entry : sources) {
                var resolvedContainers = owner.spellContainerCache().get(entry.name());
                if (resolvedContainers != null) {
                    allContainers.addAll(resolvedContainers);
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
        for (var entry: owner.serverSideSpellContainers().entrySet()) {
            allContainers.add(new SourcedContainer(entry.getKey(), null, entry.getValue()));
        }

        if (updated) {
            owner.spellModifierCache().clear();
            // Updates active equipment sets on the player (attribute set bonuses),
            // appends to `allContainers` from active equipment sets (spell set bonuses)
            updateEquipmentSets(player, allContainers);

            // System.out.println("Updating spell containers for " + player.getName());
            var heldItemStack = player.getMainHandStack();
            var heldContainer = SpellContainerHelper.containerFromItemStack(heldItemStack);
            var activeContainer = SpellContainer.EMPTY;
            List<RegistryEntry<Spell>> activeSpells = List.of();
            if (heldContainer != null && heldContainer.isResolver()) {
                var merged = mergedContainerSources1(allContainers, heldContainer, Spell.Type.ACTIVE, player.getWorld());
                activeContainer = merged.container();
                activeSpells = merged.spells();
            }
            List<RegistryEntry<Spell>> passiveSpells = mergedContainerSources(allContainers, null, null, Spell.Type.PASSIVE, player.getWorld());

            var registry = SpellRegistry.from(player.getWorld());
            LinkedHashSet<RegistryEntry<Spell>> modifiers = new LinkedHashSet<>();
            for (var container : allContainers) {
                var spellContainer = container.container();
                for (var idString : spellContainer.spell_ids()) {
                    var id = Identifier.of(idString);
                    var spell = registry.getEntry(id).orElse(null);
                    if (spell != null && spell.value().type == Spell.Type.MODIFIER) {
                        modifiers.add(spell);
                    }
                }
            }

            ((Owner) player).setSpellContainers(new Result(activeContainer, activeSpells, passiveSpells, modifiers.stream().toList(), allContainers));
        }
    }

    public static List<RegistryEntry<Spell>> mergedContainerSources(List<SourcedContainer> sources, @Nullable SpellContainer.ContentType contentType, @Nullable String accessParams, Spell.Type type, World world) {
        if (sources.isEmpty()) {
            return List.of();
        }
        var spells = new ArrayList<RegistryEntry<Spell>>();
        var registry = SpellRegistry.from(world);

        TagKey<Spell> spellTag = null;
        if (accessParams != null && !accessParams.isEmpty()) {
            if (contentType == SpellContainer.ContentType.TAG) {
                var id = Identifier.tryParse(accessParams);
                if (id != null) {
                    spellTag = TagKey.of(SpellRegistry.KEY, id);
                }
            }
        }

        for (var source : sources) {
            var container = source.container();
            if (type == Spell.Type.ACTIVE && source.name.equals("off_hand")) {
                if (!SpellEngineMod.config.spell_container_from_offhand_any) {
                    if (!container.slotMatches(EquipmentSlot.OFFHAND.asString())) {
                        continue;
                    }
                }
            }
            for (var idString : container.spell_ids()) {
                var id = Identifier.of(idString);
                var spell = registry.getEntry(id).orElse(null);
                if (spell != null && spell.value().type == type
                        && ( spellMatchesContentType(spell, contentType, spellTag) )) {
                    spells.add(spell);
                }
            }
        }

        return spells;
    }

    private static boolean spellMatchesContentType(RegistryEntry<Spell> spellEntry, @Nullable SpellContainer.ContentType contentType, @Nullable TagKey<Spell> spellTag) {
        if (contentType == null || contentType == SpellContainer.ContentType.ANY) {
            return true;
        }
        if (contentType == SpellContainer.ContentType.TAG) {
            return spellTag != null && spellEntry.isIn(spellTag);
        }
        var spell = spellEntry.value();
        var matches = switch (spell.school.archetype) {
            case ARCHERY -> contentType == SpellContainer.ContentType.ARCHERY;
            case MAGIC, MELEE -> contentType == SpellContainer.ContentType.MAGIC;
            default -> false;
        };
        if (spell.secondary_archetype != null) {
            matches = matches || switch (spell.secondary_archetype) {
                case ARCHERY -> contentType == SpellContainer.ContentType.ARCHERY;
                case MAGIC, MELEE -> contentType == SpellContainer.ContentType.MAGIC;
                case ANY -> true;
                default -> false;
            };
        }
        return matches;
    }

    public record MergeResult(SpellContainer container, List<RegistryEntry<Spell>> spells) {
        public static final MergeResult EMPTY = new MergeResult(SpellContainer.EMPTY, List.of());
    }
    public static MergeResult mergedContainerSources1(List<SourcedContainer> sources, SpellContainer heldContainer, Spell.Type type, World world) { // FIXME: NAME
        if (sources.isEmpty() || heldContainer.access() == SpellContainer.ContentType.NONE) {
            return MergeResult.EMPTY;
        }
        if (heldContainer.access() == SpellContainer.ContentType.CONTAINED) {
            var spells = new ArrayList<RegistryEntry<Spell>>();
            var registry = SpellRegistry.from(world);
            for (var idString : heldContainer.spell_ids()) {
                var id = Identifier.of(idString);
                var spell = registry.getEntry(id).orElse(null);
                if (spell != null && spell.value().type == type) {
                    spells.add(spell);
                }
            }
            return new MergeResult(heldContainer, spells);
        }

        var contentType = heldContainer.access();

        var spells = mergedContainerSources(sources, contentType, heldContainer.access_param(), type, world);

        var spellIds = new LinkedHashSet<String>(); // We need the IDs only, but remove duplicates
        for (var spell : spells) {
            spellIds.add(spell.getKey().get().getValue().toString());
        }

        // System.out.println("Updated for " + type + ", Spell IDs: " + spellIds);

        var finalContentType = contentType != null ? contentType : SpellContainer.ContentType.NONE;
        var container = new SpellContainer(finalContentType, "", null, "", 0, new ArrayList<>(spellIds), 0);
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

    private static void updateEquipmentSets(PlayerEntity player, ArrayList<SourcedContainer> allContainers) {
        ArrayList<EquipmentSet.SourcedItemStack> equipmentStacks = new ArrayList<>();
        for (var entry : itemSources) {
            final var sourceName = entry.name();
            var stacks = entry.source().getSpellContainerItemStacks(player, sourceName);
            stacks.stream()
                    .map(stack -> new EquipmentSet.SourcedItemStack(stack, sourceName))
                    .forEach(equipmentStacks::add);
        }
        var equipmentSets = EquipmentSet.collectFrom(equipmentStacks, player.getWorld());
        ((EquipmentSet.Owner) player).setActiveEquipmentSets(equipmentSets);
        allContainers.addAll(sourcedContainersFrom(equipmentSets));
    }

    private static List<SourcedContainer> sourcedContainersFrom(List<EquipmentSet.Result> results) {
        var spellContainers = new ArrayList<SourcedContainer>();
        for (var result : results) {
            var set = result.set().value();
            for (var bonus: set.bonuses()) {
                if (result.items().size() >= bonus.requiredPieceCount()
                        && bonus.spells() != null) {
                    spellContainers.add(new SourcedContainer(set.name(), result.items().getFirst(), bonus.spells()));
                }
            }
        }
        return spellContainers;
    }
}
