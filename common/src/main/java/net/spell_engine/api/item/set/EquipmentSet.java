package net.spell_engine.api.item.set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.tags.SpellEngineItemTags;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EquipmentSet {

    public record Bonus(
            int requiredPieceCount,
            @Nullable ItemAttributeModifiers attributes,
            @Nullable SpellContainer spells
    ) {
        public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("required_piece_count").forGetter(Bonus::requiredPieceCount),
                ItemAttributeModifiers.CODEC.optionalFieldOf("attributes").forGetter(Bonus::getAttributes),
                SpellContainer.CODEC.optionalFieldOf("spells").forGetter(Bonus::getSpells)
        ).apply(instance, Bonus::create));
        public Optional<ItemAttributeModifiers> getAttributes() {
            return Optional.ofNullable(attributes);
        }
        public Optional<SpellContainer> getSpells() {
            return Optional.ofNullable(spells);
        }
        public static Bonus create(int requiredPieceCount, Optional<ItemAttributeModifiers> attributes, Optional<SpellContainer> spells) {
            return new Bonus(requiredPieceCount, attributes.orElse(null), spells.orElse(null));
        }

        public static Bonus withSpells(int requiredPieceCount, SpellContainer spells) {
            return new Bonus(requiredPieceCount, null, spells);
        }
        public static Bonus withAttributes(int requiredPieceCount, ItemAttributeModifiers attributes) {
            return new Bonus(requiredPieceCount, attributes, null);
        }
    }

    public record Definition(
            String name,
            RegistryEntryList<Item> items,
            List<Bonus> bonuses
    ) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Definition::name),
                RegistryCodecs.entryList(RegistryKeys.ITEM).fieldOf("items").forGetter(Definition::items),
                Bonus.CODEC.listOf().fieldOf("bonuses").forGetter(Definition::bonuses)
        ).apply(instance, Definition::new));
    }
    public static String translationKey(RegistryEntry<Definition> entry) {
        return translationKey(entry.getKey().get().getValue());
    }
    public static String translationKey(Identifier id) {
        return "equipment_set." + id.getNamespace() + "." + id.getPath();
    }

//    public record DataComponent(Identifier id) {
//        public static final Codec<DataComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
//                Identifier.CODEC.fieldOf("id").forGetter(DataComponent::id)
//        ).apply(instance, DataComponent::new));
//    }

    public record Result(RegistryEntry<EquipmentSet.Definition> set, List<ItemStack> items) { }

    public record SourcedItemStack(ItemStack itemstack, String sourceName) { }
    public static List<Result> collectFrom(List<SourcedItemStack> stacks, World world) {
        LinkedHashMap<Identifier, LinkedHashMap<RegistryKey<Item>, ItemStack> > sets = new LinkedHashMap<>();
        for (var sourcedStack : stacks) {
            var stack = sourcedStack.itemstack();
            var id = SpellItemData.getEquipmentSet(stack);
            if (id != null) {
                var itemKey = Registries.ITEM.getKey(stack.getItem());
                if (itemKey.isEmpty()) {
                    continue;
                }
                if (sourcedStack.sourceName.contains("hand") && !stack.isIn(SpellEngineItemTags.HANDHELD)) {
                    // Prevent armor counted from hands
                    continue;
                }
                var items = sets.computeIfAbsent(id, k -> new LinkedHashMap<>());
                items.put(itemKey.get(), stack);
            }
        }
        var registry = world.getRegistryManager().get(EquipmentSetRegistry.KEY);
        List<Result> results = new ArrayList<>();
        for (var entry : sets.entrySet()) {
            var setId = entry.getKey();
            var set = registry.getEntry(RegistryKey.of(EquipmentSetRegistry.KEY, setId));
            if (set.isPresent()) {
                var items = entry.getValue().values().stream().toList();
                results.add(new Result(set.get(), items));
            }
        }
        return results;
    }

    public interface Owner {
        List<Result> getActiveEquipmentSets();
        void setActiveEquipmentSets(List<Result> results);
    }

    public static List<ItemAttributeModifiers> attributesFrom(List<Result> results) {
        var attributeModifiers = new ArrayList<ItemAttributeModifiers>();
        for (var result : results) {
            var set = result.set.value();
            for (var bonus: set.bonuses) {
                if (result.items().size() >= bonus.requiredPieceCount
                    && bonus.attributes != null) {
                    attributeModifiers.add(bonus.attributes);
                }
            }
        }
        return attributeModifiers;
    }
}

