package net.spell_engine.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

public class SpellEngineItems {
    public static class Group {
        public static Identifier ID = Identifier.of(SpellEngineMod.ID, "generic");
        public static RegistryKey<ItemGroup> KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), ID);
        public static ItemGroup SPELLS = FabricItemGroup.builder()
                .icon(() -> new ItemStack(SpellBindingBlock.ITEM))
                .displayName(Text.translatable("itemGroup." + SpellEngineMod.ID + ".general"))
                .build();
    }

    @Nullable private static Supplier<Item> spellScrollFactory = () -> new ScrollItem(new Item.Settings().maxCount(1));
    public static void setSpellScrollFactory(Supplier<Item> factory) {
        if (spellScrollFactory != null) { return; }
        spellScrollFactory = factory;
    }
    @Nullable private static Function<Identifier, ISpellBookItem> spellBookFactory = (poolId) -> new SpellBookItem(poolId, new Item.Settings().maxCount(1));
    public static void setSpellBookFactory(Function<Identifier, ISpellBookItem> factory) {
        if (spellBookFactory != null) { return; }
        spellBookFactory = factory;
    }

    public static final Lazy<Item> SCROLL = new Lazy<>(() -> {
        return spellScrollFactory != null ? spellScrollFactory.get() : new ScrollItem(new Item.Settings().maxCount(1));
    });
    public static ISpellBookItem createBook(Identifier poolId) {
        return spellBookFactory != null ? spellBookFactory.apply(poolId) : new SpellBookItem(poolId, new Item.Settings().maxCount(1));
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, Group.KEY, Group.SPELLS);
        Registry.register(Registries.ITEM, SpellBinding.ID, SpellBindingBlock.ITEM);
        Registry.register(Registries.ITEM, ScrollItem.ID, SCROLL.get());
        ItemGroupEvents.modifyEntriesEvent(Group.KEY).register(content -> {
            content.add(SpellBindingBlock.ITEM);

            var registryWrapper = content.getContext().lookup().getWrapperOrThrow(SpellRegistry.KEY);
            registryWrapper.streamEntries()
                    .sorted(Comparator.comparing(a -> a.getKey().get().getValue().getNamespace() + "_" + a.value().tier + "_" + a.getKey().get().getValue().getPath()))
                    .forEach((entry) -> {
                        var scroll = new ItemStack(SCROLL.get());
                        if (ScrollItem.applySpell(scroll, entry, ScrollItem.resolveSpellPool(registryWrapper, entry))) {
                            content.add(scroll);
                        }
                    });
        });
    }
}
