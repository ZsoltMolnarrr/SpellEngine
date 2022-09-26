package net.combatspells.attribute_assigner;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.combatspells.attribute_assigner.ItemAttributeModifier.Operation.ADD;

public class AttributeAssigner {

    public static Map<Identifier, List<ItemAttributeModifier>> assignemnts = new HashMap();

    public static void initialize() {
        var fire = new ItemAttributeModifier("spelldamage:fire", 4);
        fire.operation = ADD;
        assignemnts.put(
                new Identifier("minecraft", "wooden_sword"),
                List.of(fire));


    }
}
