package net.spell_engine.api.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerTemplates;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.item.SpellEngineItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Deprecated(forRemoval = true)
public class SpellBooks {
    @Deprecated(forRemoval = true)
    public static final ArrayList<ISpellBookItem> all = new ArrayList<>();

    @Deprecated(forRemoval = true)
    public static List<ISpellBookItem> sorted(World world) {
        return SpellBooks.all
                .stream()
                .sorted(Comparator.comparing(spellBookItem -> spellBookItem.getPoolId().toString()))
                .filter(spellBookItem -> {
                    var pool = SpellRegistry.entries(world, spellBookItem.getPoolId());
                    return pool != null && !pool.isEmpty();
                })
                .collect(Collectors.toList());
    }

    @Deprecated(forRemoval = true)
    public static ISpellBookItem create(Identifier poolId) {
        return create(poolId, 0);
    }

    @Deprecated(forRemoval = true)
    public static ISpellBookItem create(Identifier poolId, SpellContainer.ContentType contentType) {
        return create(poolId, contentType, 0);
    }

    @Deprecated(forRemoval = true)
    public static ISpellBookItem create(Identifier poolId, SpellContainer.ContentType contentType, int maxSpellCount) {
        var config = SpellContainerTemplates.config.safeValue();
        var baseContainer = config.spell_book != null ? config.spell_book : SpellContainerTemplates.defaults().spell_book;
        var container = baseContainer.withBindingPool(poolId).withMaxSpellCount(maxSpellCount);
        SpellAssignments.book_containers.put(itemIdFor(poolId), container);
        Platform.util().awakeSlotModCompat();
        ISpellBookItem book = SpellEngineItems.createBook(poolId);
        all.add(book);
        return book;
    }

    @Deprecated(forRemoval = true)
    public static ISpellBookItem create(Identifier id, int maxSpellCount) {
        var config = SpellContainerTemplates.config.safeValue();
        var baseContainer = config.spell_book != null ? config.spell_book : SpellContainerTemplates.defaults().spell_book;
        var spellPoolId = spellPoolFor(id);
        var container = baseContainer.withBindingPool(spellPoolId).withMaxSpellCount(maxSpellCount);
        SpellAssignments.book_containers.put(itemIdFor(id), container);
        Platform.util().awakeSlotModCompat();
        ISpellBookItem book = SpellEngineItems.createBook(spellPoolId);
        all.add(book);
        return book;
    }

    @Deprecated(forRemoval = true)
    public static Identifier spellPoolFor(Identifier id) {
        return Identifier.of(id.getNamespace(), SpellTags.SPELL_BOOK_PREFIX + id.getPath());
    }

    @Deprecated(forRemoval = true)
    public static Identifier itemIdFor(Identifier id) {
        // DO NOT REFACTOR THIS!
        // Spell Book items must remain under different IDs
        // so when setting cooldown on them, they don't get all the same cooldown
        // (This is a restriction of vanilla `ItemCooldownManager`)
        return Identifier.of(id.getNamespace(), id.getPath() + "_spell_book");
    }

    @Deprecated(forRemoval = true)
    public static void register(ISpellBookItem spellBook) {
        if (spellBook instanceof Item) {
            Registry.register(Registries.ITEM, itemIdFor(spellBook.getPoolId()), (Item) spellBook);
        } else {
            throw new IllegalArgumentException("SpellBookItem must be an Item");
        }
    }

    @Deprecated(forRemoval = true)
    public static void register(Identifier itemId, ISpellBookItem spellBook) {
        if (spellBook instanceof Item) {
            Registry.register(Registries.ITEM, itemId, (Item) spellBook);
        } else {
            throw new IllegalArgumentException("SpellBookItem must be an Item");
        }
    }

    @Deprecated(forRemoval = true)
    public static void createAndRegister(Identifier id, RegistryKey<ItemGroup> itemGroupKey) {
        var item = create(id);
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> content.add(item));
        register(itemIdFor(id), item);
    }

    @Deprecated(forRemoval = true)
    public static void createAndRegister(Identifier id, SpellContainer.ContentType contentType, RegistryKey<ItemGroup> itemGroupKey) {
        var item = create(id);
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> content.add(item));
        register(itemIdFor(id), item);
    }
}
