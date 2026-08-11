package net.spell_engine.api.item.set;

import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.Platform;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.List;

public class EquipmentSetFeature {
    public static void init() {
        // Same codec used for local storage and network sync.
        Platform.util().registerSyncedDataRegistry(EquipmentSetRegistry.KEY, EquipmentSet.Definition.CODEC, EquipmentSet.Definition.CODEC);
        SpellContainerSource.addSource(new SpellContainerSource.Entry("equipment_set", new SpellContainerSource.Source() {
            @Override
            public List<SpellContainerSource.SourcedContainer> getSpellContainers(PlayerEntity player, String name) {
                return List.of();
            }
        }, null)); // Dirty checker relying on equipment changes
    }
}
