package net.spell_engine.api.spell;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.spell.container.SpellContainer;

import java.util.function.UnaryOperator;

public class SpellDataComponents {
    public static final ComponentType<SpellContainer> SPELL_CONTAINER = register(Identifier.of(SpellEngineMod.ID, "spell_container"),
            builder -> builder.codec(SpellContainer.CODEC)
    );
    public static final ComponentType<EquipmentSet.DataComponent> EQUIPMENT_SET = register(Identifier.of(SpellEngineMod.ID, "equipment_set"),
            builder -> builder.codec(EquipmentSet.DataComponent.CODEC)
    );

    private static <T> ComponentType<T> register(Identifier id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, id, ((ComponentType.Builder)builderOperator.apply(ComponentType.builder())).build());
    }
}
