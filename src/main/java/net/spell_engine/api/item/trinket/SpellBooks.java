package net.spell_engine.api.item.trinket;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.compat.TrinketsCompat;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// TODO: This class should probably be moved to net.spell_engine.api.item as it seems to be mod-agnostic
public class SpellBooks {
    private static final ArrayList<SpellBookItem> all = new ArrayList<>();

    public static List<SpellBookItem> sorted() {
        return SpellBooks.all
                .stream()
                .sorted(Comparator.comparing(spellBookItem -> spellBookItem.getPoolId().toString()))
                .filter(spellBookItem -> {
                    var pool = SpellRegistry.spellPool(spellBookItem.getPoolId());
                    return pool != null && pool.craftable();
                })
                .collect(Collectors.toList());
    }

    public static SpellBookItem create(Identifier poolId) {
        return create(poolId, SpellContainer.ContentType.MAGIC);
    }

    public static SpellBookItem create(Identifier poolId, SpellContainer.ContentType contentType) {
        var container = new SpellContainer(contentType, false, poolId.toString(), 0, List.of());
        SpellRegistry.book_containers.put(itemIdFor(poolId), container);
        SpellBookItem book = null;
        if (TrinketsCompat.isEnabled()) {
            book = new SpellBookTrinketItem(poolId, new FabricItemSettings().maxCount(1));
        }
        // TODO: Add support for Curios
        else {
            book = new SpellBookVanillaItem(poolId, new FabricItemSettings().maxCount(1));
        }
        all.add(book);
        return book;
    }

    public static Identifier itemIdFor(Identifier poolId) {
        return new Identifier(poolId.getNamespace(), poolId.getPath() + "_spell_book");
    }

    public static void register(SpellBookItem spellBook) {
        if (spellBook instanceof Item) {
            Registry.register(Registries.ITEM, itemIdFor(spellBook.getPoolId()), (Item) spellBook);
        } else {
            throw new IllegalArgumentException("SpellBookItem must be an Item");
        }
    }

    public static void createAndRegister(Identifier poolId, RegistryKey<ItemGroup> itemGroupKey) {
        createAndRegister(poolId, SpellContainer.ContentType.MAGIC, itemGroupKey);
    }

    public static void createAndRegister(Identifier poolId, SpellContainer.ContentType contentType, RegistryKey<ItemGroup> itemGroupKey) {
        var item = create(poolId, contentType);
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> content.add(item));
        register(item);
    }
}
