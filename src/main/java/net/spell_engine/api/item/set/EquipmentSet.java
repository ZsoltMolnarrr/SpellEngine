package net.spell_engine.api.item.set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EquipmentSet {

    public record Bonus(
            int requiredPieceCount,
            @Nullable AttributeModifiersComponent attributes,
            @Nullable SpellContainer spells
    ) {
        public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("required_piece_count").forGetter(Bonus::requiredPieceCount),
                AttributeModifiersComponent.CODEC.optionalFieldOf("attributes").forGetter(Bonus::getAttributes),
                SpellContainer.CODEC.optionalFieldOf("spells").forGetter(Bonus::getSpells)
        ).apply(instance, Bonus::create));
        public Optional<AttributeModifiersComponent> getAttributes() {
            return Optional.ofNullable(attributes);
        }
        public Optional<SpellContainer> getSpells() {
            return Optional.ofNullable(spells);
        }
        public static Bonus create(int requiredPieceCount, Optional<AttributeModifiersComponent> attributes, Optional<SpellContainer> spells) {
            return new Bonus(requiredPieceCount, attributes.orElse(null), spells.orElse(null));
        }

        public static Bonus withSpells(int requiredPieceCount, SpellContainer spells) {
            return new Bonus(requiredPieceCount, null, spells);
        }
        public static Bonus withAttributes(int requiredPieceCount, AttributeModifiersComponent attributes) {
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

    public record DataComponent(Identifier id) {
        public static final Codec<DataComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(DataComponent::id)
        ).apply(instance, DataComponent::new));
    }


    public record Result(RegistryEntry<EquipmentSet.Definition> set, ArrayList<ItemStack> items) { }

    public static List<Result> collectFrom(List<ItemStack> stacks, World world) {
        LinkedHashMap<Identifier, ArrayList<ItemStack> > sets = new LinkedHashMap<>();
        for (var stack : stacks) {
            var component = stack.get(SpellDataComponents.EQUIPMENT_SET);
            if (component != null) {
                var id = component.id();
                var items = sets.computeIfAbsent(id, k -> new ArrayList<>());
                sets.get(id).add(stack);
            }
        }
        var registry = world.getRegistryManager().get(EquipmentSetRegistry.KEY);
        List<Result> results = new ArrayList<>();
        for (var entry : sets.entrySet()) {
            var setId = entry.getKey();
            var set = registry.getEntry(setId);
            if (set.isPresent()) {
                var items = entry.getValue();
                results.add(new Result(set.get(), items));
            }
        }
        return results;
    }

    public interface Owner {
        List<Result> getActiveEquipmentSets();
        void setActiveEquipmentSets(List<Result> results);
    }

    public static List<AttributeModifiersComponent> attributesFrom(List<Result> results) {
        var attributeModifiers = new ArrayList<AttributeModifiersComponent>();
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

