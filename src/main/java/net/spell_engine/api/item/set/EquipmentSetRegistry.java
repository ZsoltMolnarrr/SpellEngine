package net.spell_engine.api.item.set;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class EquipmentSetRegistry {
    public static final Identifier ID = Identifier.ofVanilla("equipment_set");
    public static final RegistryKey<Registry<EquipmentSet.Definition>> KEY = RegistryKey.ofRegistry(ID);
    public static Registry<EquipmentSet.Definition> from(World world) {
        return world.getRegistryManager().get(KEY);
    }
}
