package net.spell_engine.api.item.trinket;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SpellBooks {
    private static final ArrayList<SpellBookItem> all = new ArrayList<>();
    public static List<SpellBookItem> sorted() {
        return SpellBooks.all
                .stream()
                .sorted(Comparator.comparing(spellBookItem -> spellBookItem.poolId.toString()))
                .collect(Collectors.toList());
    }

    public static SpellBookItem create(Identifier poolId) {
        var container = new SpellContainer(false, poolId.toString(), 0, List.of());
        SpellRegistry.book_containers.put(itemIdFor(poolId), container);
        var book = new SpellBookItem(poolId, new FabricItemSettings().maxCount(1));
        all.add(book);
        return book;
    }

    public static Identifier itemIdFor(Identifier poolId) {
        return new Identifier(poolId.getNamespace(), poolId.getPath() + "_spell_book");
    }

    public static void register(SpellBookItem spellBook) {
        Registry.register(Registries.ITEM, itemIdFor(spellBook.poolId), spellBook);
    }

    public static void createAndRegister(Identifier poolId, RegistryKey<ItemGroup> itemGroupKey) {
        var item = create(poolId);
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> {
            content.add(item);
        });
        register(item);
    }
}
