package net.spell_engine.item;

import net.spell_engine.PlatformEvents;
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
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.compat.SlotModCompat;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;

import java.util.Comparator;

public class SpellEngineItems {
    public static class Group {
        public static Identifier ID = Identifier.of(SpellEngineMod.ID, "generic");
        public static RegistryKey<ItemGroup> KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), ID);
        // Vanilla ItemGroup.Builder (loader-neutral) replaces FabricItemGroup.builder(); row/column
        // are irrelevant for a separately registered group.
        public static ItemGroup SPELLS = new ItemGroup.Builder(ItemGroup.Row.TOP, 0)
                .icon(() -> new ItemStack(SpellBindingBlock.ITEM))
                .displayName(Text.translatable("itemGroup." + SpellEngineMod.ID + ".general"))
                .build();
    }

    public static final Lazy<Item> SCROLL = new Lazy<>(() -> {
        var settings = new Item.Settings().maxCount(1);
        var args = new SlotModCompat.SpellScrollArs(settings);
        var factory = SlotModCompat.spellScrollFactory;
        return factory != null ? factory.apply(args) : new ScrollItem(args.settings());
    });

    public static final Lazy<Item> SPELL_BOOK = new Lazy<>(() -> {
        var settings = new Item.Settings().maxCount(1);
        var args = new SlotModCompat.SpellBookArgs(settings);
        var factory = SlotModCompat.spellBookFactory;
        return factory != null ? factory.apply(args) : new UniversalSpellBookItem(args.settings());
    });

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, Group.KEY, Group.SPELLS);
        Registry.register(Registries.ITEM, SpellBinding.ID, SpellBindingBlock.ITEM);
        Registry.register(Registries.ITEM, ScrollItem.ID, SCROLL.get());
        Registry.register(Registries.ITEM, UniversalSpellBookItem.ID, SPELL_BOOK.get());
        PlatformEvents.onItemGroupModify(Group.KEY, (content, context) -> {
            content.add(SpellBindingBlock.ITEM);

            var registryWrapper = context.lookup().getWrapperOrThrow(SpellRegistry.KEY);

            // Spell book variants from tags
            var spellBookTags = registryWrapper.streamTags()
                    .filter(tag ->
                            tag.getTagKey().isPresent()
                                    && tag.getTagKey().get().id().getPath().startsWith(SpellTags.SPELL_BOOK_PREFIX)
                    )
                    .sorted(Comparator.comparing(tag ->
                            tag.getTagKey().get().id().getNamespace() + "_" + tag.getTagKey().get().id().getPath()))
                    .toList();
            for (var spellBookTag : spellBookTags) {
                var tagKey = spellBookTag.getTagKey().get();
                var spellBook = new ItemStack(SPELL_BOOK.get());
                if (UniversalSpellBookItem.applyFromTag(spellBook, tagKey)) {
                    content.add(spellBook);
                }
            }

            var scrollTags = registryWrapper.streamTags()
                    .filter(tag ->
                            tag.getTagKey().isPresent() && tag.getTagKey().get().id().getPath().startsWith(SpellTags.SPELL_SCROLL_PREFIX)
                    )
                    .sorted(Comparator.comparing(tag -> tag.getTagKey().get().id().getNamespace() + "_" + tag.getTagKey().get().id().getPath()))
                    .toList();
            for (var scrollTag: scrollTags) {
                scrollTag.stream()
                        .sorted(SpellContainerHelper.catalogEntrySorter)
                        .forEach((entry) -> {
                            var scroll = new ItemStack(SCROLL.get());
                            ScrollItem.applySpell(scroll, entry, scrollTag.getTag());
                            content.add(scroll);
                        });
            }
        });
    }
}
