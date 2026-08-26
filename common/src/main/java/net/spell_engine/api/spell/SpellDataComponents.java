package net.spell_engine.api.spell;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;

import java.util.function.UnaryOperator;

public class SpellDataComponents {
    public static final DataComponentType<SpellContainer> SPELL_CONTAINER = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_container"),
            builder -> builder.persistent(SpellContainer.CODEC)
    );
    public static final DataComponentType<SpellChoice> SPELL_CHOICE = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_choice"),
            builder -> builder.persistent(SpellChoice.CODEC)
    );
    public static final DataComponentType<Identifier> EQUIPMENT_SET = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "equipment_set"),
            builder -> builder.persistent(Identifier.CODEC)
    );

    private static <T> DataComponentType<T> register(Identifier id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, ((DataComponentType.Builder)builderOperator.apply(DataComponentType.builder())).build());
    }
}
