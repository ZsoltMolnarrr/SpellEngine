package net.spell_engine.api.item.trinket;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellRegistry;

import java.util.List;

public class SpellBooks {

    public static SpellBookItem create(Identifier poolId, ItemGroup itemGroup) {
        var container = new SpellContainer(poolId.toString(), 3, List.of());
        SpellRegistry.book_containers.put(itemIdFor(poolId), container);
        return new SpellBookItem(poolId, new FabricItemSettings().maxCount(1).group(itemGroup));
    }

    public static Identifier itemIdFor(Identifier poolId) {
        return new Identifier(poolId.getNamespace(), "spell_book" + "." + poolId.getPath());
    }

    public static void register(SpellBookItem spellBook) {
        Registry.register(Registry.ITEM, itemIdFor(spellBook.poolId), spellBook);
    }

    public static void createAndRegister(Identifier poolId, ItemGroup itemGroup) {
        register(create(poolId, itemGroup));
    }
}
