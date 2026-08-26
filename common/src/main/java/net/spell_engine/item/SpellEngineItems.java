package net.spell_engine.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.Platform;
import net.spell_engine.PlatformEvents;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.compat.SlotModCompat;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;

import java.util.Comparator;

public class SpellEngineItems {
    public static class Group {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "generic");
        public static ResourceKey<CreativeModeTab> KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), ID);
        // Vanilla ItemGroup.Builder (loader-neutral) replaces FabricItemGroup.builder(); row/column
        // are irrelevant for a separately registered group.
        public static CreativeModeTab SPELLS = new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 0)
                .icon(() -> new ItemStack(SpellBindingBlock.ITEM))
                .title(Component.translatable("itemGroup." + SpellEngineMod.ID + ".general"))
                .build();
    }

    public static final Supplier<Item> SCROLL = Suppliers.memoize(() -> {
        // Slot mod compat must install its item factories before the first access,
        // items get created (thus factories read) during item registration, which
        // runs before the loader entrypoints reach compat init.
        Platform.util().awakeSlotModCompat();
        var settings = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ScrollItem.ID)).stacksTo(1);
        var args = new SlotModCompat.SpellScrollArs(settings);
        var factory = SlotModCompat.spellScrollFactory;
        return factory != null ? factory.apply(args) : new ScrollItem(args.settings());
    });

    public static final Supplier<Item> SPELL_BOOK = Suppliers.memoize(() -> {
        Platform.util().awakeSlotModCompat();
        var settings = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, UniversalSpellBookItem.ID)).stacksTo(1);
        var args = new SlotModCompat.SpellBookArgs(settings);
        var factory = SlotModCompat.spellBookFactory;
        return factory != null ? factory.apply(args) : new UniversalSpellBookItem(args.settings());
    });

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Group.KEY, Group.SPELLS);
        Registry.register(BuiltInRegistries.ITEM, SpellBinding.ID, SpellBindingBlock.ITEM);
        Registry.register(BuiltInRegistries.ITEM, ScrollItem.ID, SCROLL.get());
        Registry.register(BuiltInRegistries.ITEM, UniversalSpellBookItem.ID, SPELL_BOOK.get());
        PlatformEvents.onItemGroupModify(Group.KEY, (content, context) -> {
            content.accept(SpellBindingBlock.ITEM);

            var registryWrapper = context.holders().lookupOrThrow(SpellRegistry.KEY);

            // Spell book variants from tags
            var spellBookTags = registryWrapper.listTags()
                    .filter(tag ->
                            tag.key().location().getPath().startsWith(SpellTags.SPELL_BOOK_PREFIX)
                    )
                    .sorted(Comparator.comparing(tag ->
                            tag.key().location().getNamespace() + "_" + tag.key().location().getPath()))
                    .toList();
            for (var spellBookTag : spellBookTags) {
                var tagKey = spellBookTag.key();
                var spellBook = new ItemStack(SPELL_BOOK.get());
                if (UniversalSpellBookItem.applyFromTag(spellBook, tagKey)) {
                    content.accept(spellBook);
                }
            }

            var scrollTags = registryWrapper.listTags()
                    .filter(tag ->
                            tag.key().location().getPath().startsWith(SpellTags.SPELL_SCROLL_PREFIX)
                    )
                    .sorted(Comparator.comparing(tag -> tag.key().location().getNamespace() + "_" + tag.key().location().getPath()))
                    .toList();
            for (var scrollTag: scrollTags) {
                scrollTag.stream()
                        .sorted(SpellContainerHelper.catalogEntrySorter)
                        .forEach((entry) -> {
                            var scroll = new ItemStack(SCROLL.get());
                            ScrollItem.applySpell(scroll, entry, scrollTag.key());
                            content.accept(scroll);
                        });
            }
        });
    }
}
