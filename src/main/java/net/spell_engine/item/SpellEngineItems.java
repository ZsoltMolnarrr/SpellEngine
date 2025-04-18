package net.spell_engine.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.SpellTagsNumbered;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.compat.trinkets.SpellScrollTrinketItem;
import net.spell_engine.compat.trinkets.TrinketsCompat;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class SpellEngineItems {
    public static class Group {
        public static Identifier ID = Identifier.of(SpellEngineMod.ID, "generic");
        public static RegistryKey<ItemGroup> KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), ID);
        public static ItemGroup SPELLS = FabricItemGroup.builder()
                .icon(() -> new ItemStack(SpellBindingBlock.ITEM))
                .displayName(Text.translatable("itemGroup." + SpellEngineMod.ID + ".general"))
                .build();
    }

    public static final Lazy<Item> SCROLL = new Lazy<>(() -> {
        if (TrinketsCompat.isEnabled()) {
            return new SpellScrollTrinketItem(new Item.Settings().maxCount(1), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent());
        } else {
            return new ScrollItem(new Item.Settings().maxCount(1));
        }
    });

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
                        if (ScrollItem.applySpell(scroll, entry, resolveSpellPool(registryWrapper, entry))) {
                            content.add(scroll);
                        }
                    });
        });
    }

    @Nullable private static TagKey<Spell> resolveSpellPool(RegistryWrapper<Spell> wrapper, RegistryEntry<Spell> spellEntry) {
        // Find the first tag in which spellEntry is contained
        var tag = wrapper.streamTags()
                .filter(t -> SpellTagsNumbered.isRegistered(t.getTagKey().get().id()) && t.contains(spellEntry))
                .findFirst();
        if (tag.isPresent()) {
            return tag.get().getTagKey().get();
        } else {
            return null;
        }
    }
}
