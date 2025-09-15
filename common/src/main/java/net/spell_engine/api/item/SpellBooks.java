package net.spell_engine.api.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.compat.trinkets.SpellBookTrinketItem;
import net.spell_engine.compat.trinkets.TrinketsCompat;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.internals.container.SpellAssignments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// TODO: This class should probably be moved to net.spell_engine.api.item as it seems to be mod-agnostic
public class SpellBooks {
    public static final ArrayList<ISpellBookItem> all = new ArrayList<>();

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

    public static ISpellBookItem create(Identifier poolId) {
        return create(poolId, SpellContainer.ContentType.MAGIC);
    }

    public static ISpellBookItem create(Identifier poolId, SpellContainer.ContentType contentType) {
        return create(poolId, contentType, 0);
    }

    public static ISpellBookItem create(Identifier poolId, SpellContainer.ContentType contentType, int maxSpellCount) {
        var container = new SpellContainer(contentType, false, poolId.toString(), maxSpellCount, List.of());
        SpellAssignments.book_containers.put(itemIdFor(poolId), container);
        ISpellBookItem book = null;
        TrinketsCompat.init();
        if (TrinketsCompat.isEnabled()) {
            book = new SpellBookTrinketItem(new Item.Settings().maxCount(1), poolId, SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent());
        }
        // TODO: Add support for Curios
        else {
            book = new SpellBookItem(poolId, new Item.Settings().maxCount(1));
        }
        all.add(book);
        return book;
    }

    public static Identifier itemIdFor(Identifier poolId) {
        // DO NOT REFACTOR THIS!
        // Spell Book items must remain under different IDs
        // so when setting cooldown on them, they don't get all the same cooldown
        // (This is a restriction of vanilla `ItemCooldownManager`)
        return Identifier.of(poolId.getNamespace(), poolId.getPath() + "_spell_book");
    }

    public static void register(ISpellBookItem spellBook) {
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
