package net.spell_engine.api.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Datagen provider for equipment sets: writes `data/<ns>/equipment_set/<id>.json` through
/// `EquipmentSet.Definition.CODEC` (via the Fabric dynamic-registry provider, i.e. the same path that loads them).
///
/// Requirements:
/// - the owning `DataGeneratorEntrypoint` must call `RPGSeriesDataGen.buildRegistry(registryBuilder)` from its
///   `buildRegistry` override (it contributes `EquipmentSetRegistry.KEY` to the datagen `WrapperLookup`);
/// - the set's items must be registered by the time datagen runs (they are looked up in the item registry).
///
/// Usage:
/// ```java
/// public static class SetGen extends EquipmentSetGenerator {
///     public SetGen(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) { super(output, registries); }
///     @Override
///     public void generateEquipmentSets(Builder builder) {
///         builder.add(new Identifier("mymod", "fire_set"), "fire_set", List.of(helmetId, chestId, legsId, bootsId), List.of(bonuses...));
///     }
/// }
/// pack.addProvider(SetGen::new);
/// ```
/// Bonus attributes may reference attributes of optional mods by id
/// (`ItemAttributeModifiers.builder().add(Identifier, modifier, slot)`); they serialize whether or not that mod is
/// on the datagen runtime.
public abstract class EquipmentSetGenerator extends FabricDynamicRegistryProvider {

    public EquipmentSetGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    public record Entry(Identifier id, EquipmentSet.Definition equipmentSet) { }

    public static class Builder {
        private final RegistryEntryLookup<Item> itemLookup;
        private final List<Entry> entries = new ArrayList<>();

        private Builder(RegistryEntryLookup<Item> itemLookup) {
            this.itemLookup = itemLookup;
        }

        /// Adds a fully built definition (items already resolved to registry entries).
        public Builder add(Identifier id, EquipmentSet.Definition definition) {
            entries.add(new Entry(id, definition));
            return this;
        }

        /// Adds a set from item ids; every id must be a registered item.
        public Builder add(Identifier id, String name, List<Identifier> itemIds, List<EquipmentSet.Bonus> bonuses) {
            var items = RegistryEntryList.of(itemIds.stream()
                    .map(itemId -> itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, itemId)))
                    .toList());
            return add(id, new EquipmentSet.Definition(name, items, bonuses));
        }

        /// Adds a set from item objects; every item must be registered.
        public Builder addItems(Identifier id, String name, List<Item> items, List<EquipmentSet.Bonus> bonuses) {
            return add(id, name, items.stream().map(item -> net.minecraft.registry.Registries.ITEM.getId(item)).toList(), bonuses);
        }
    }

    public abstract void generateEquipmentSets(Builder builder);

    @Override
    protected final void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        var builder = new Builder(registries.getWrapperOrThrow(RegistryKeys.ITEM));
        generateEquipmentSets(builder);
        for (var entry : builder.entries) {
            entries.add(RegistryKey.of(EquipmentSetRegistry.KEY, entry.id()), entry.equipmentSet());
        }
    }

    @Override
    public String getName() {
        return "Equipment Set Generator";
    }
}
