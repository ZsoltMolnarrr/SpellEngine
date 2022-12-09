package net.combatspells.runes;

import net.combatspells.CombatSpells;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.spell_damage.api.MagicSchool;

import java.util.HashMap;
import java.util.Map;

public class RuneItems {
    public static Map<Identifier, Item> all;
    static {
        all = new HashMap<>();
        for(var school : MagicSchool.values()) {
            var id = new Identifier(CombatSpells.MOD_ID, school.toString().toLowerCase() + "_rune");
            var item = new Item(new FabricItemSettings().group(ItemGroup.COMBAT));
            all.put(id, item);
        }
    }
}
