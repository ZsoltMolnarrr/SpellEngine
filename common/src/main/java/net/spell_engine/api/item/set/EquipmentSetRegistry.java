package net.spell_engine.api.item.set;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class EquipmentSetRegistry {
    public static final Identifier ID = Identifier.withDefaultNamespace("equipment_set");
    public static final ResourceKey<Registry<EquipmentSet.Definition>> KEY = ResourceKey.createRegistryKey(ID);
    public static Registry<EquipmentSet.Definition> from(Level world) {
        return world.registryAccess().lookupOrThrow(KEY);
    }
}
