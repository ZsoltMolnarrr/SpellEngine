package net.spell_engine.internals;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.spell_engine.SpellEngineMod;

import java.util.List;

public class ProjectileItems {
    public static final List<String> items = List.of("fireball_projectile");
    public static void register() {
        for(var itemName: items) {
            var id = new Identifier(SpellEngineMod.ID, itemName);
            var item = new Item(new FabricItemSettings());
            Registry.register(Registry.ITEM, id, item);
        }
    }
}
