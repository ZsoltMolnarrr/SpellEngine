package net.spell_engine.api.item.set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
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

    public record Result(Identifier setId, List<RegistryKey<Item>> items) { }

    public static final Map<Identifier, Definition> REGISTRY = new HashMap<>();
    public List<SpellContainer> spellsFrom(List<Result> results) {
        var spellContainers = new ArrayList<SpellContainer>();
        for (var result : results) {
            var set = REGISTRY.get(result.setId());
            if (set == null) continue;
            for (var bonus: set.bonuses) {
                if (result.items().size() >= bonus.requiredPieceCount
                    && bonus.spells != null) {
                    spellContainers.add(bonus.spells);
                }
            }
        }
        return spellContainers;
    }

    public List<AttributeModifiersComponent> attributesFrom(List<Result> results) {
        var attributeModifiers = new ArrayList<AttributeModifiersComponent>();
        for (var result : results) {
            var set = REGISTRY.get(result.setId());
            if (set == null) continue;
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

