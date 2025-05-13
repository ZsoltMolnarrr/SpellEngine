package net.spell_engine.api.item.set;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.List;

public class EquipmentSetFeature {
    public static void init() {
        DynamicRegistries.registerSynced(EquipmentSetRegistry.KEY, EquipmentSet.Definition.CODEC);
        SpellContainerSource.addSource(new SpellContainerSource.Entry("equipment_set", new SpellContainerSource.Source() {
            @Override
            public List<SpellContainerSource.SourcedContainer> getSpellContainers(PlayerEntity player, String name) {
                return List.of();
            }
        }, null)); // Dirty checker relying on equipment changes
    }
}
