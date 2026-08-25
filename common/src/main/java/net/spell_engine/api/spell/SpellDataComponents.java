package net.spell_engine.api.spell;

import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.rpg_series.item.LazyRepair;

import java.util.function.UnaryOperator;

public class SpellDataComponents {
    public static final ComponentType<SpellContainer> SPELL_CONTAINER = register(Identifier.of(SpellEngineMod.ID, "spell_container"),
            builder -> builder.codec(SpellContainer.CODEC)
    );
    public static final ComponentType<SpellChoice> SPELL_CHOICE = register(Identifier.of(SpellEngineMod.ID, "spell_choice"),
            builder -> builder.codec(SpellChoice.CODEC)
    );
    public static final ComponentType<Identifier> EQUIPMENT_SET = register(Identifier.of(SpellEngineMod.ID, "equipment_set"),
            builder -> builder.codec(Identifier.CODEC)
    );
    /// Transient (no persistent codec, never saved/synced) item-default component carrying a lazily resolved
    /// anvil repair ingredient — see {@link LazyRepair}. Consulted by `ItemStackRepairMixin`.
    public static final ComponentType<LazyRepair> LAZY_REPAIR = register(Identifier.of(SpellEngineMod.ID, "lazy_repair"),
            builder -> builder.packetCodec(PacketCodec.unit(LazyRepair.NONE))
    );
    /// @deprecated since 1.21.11 the vanilla `minecraft:item_model` component is used instead (item-model definitions)
    @Deprecated(forRemoval = true)
    public static final ComponentType<Identifier> ITEM_MODEL = register(Identifier.of(SpellEngineMod.ID, "item_model"),
            builder -> builder.codec(Identifier.CODEC)
    );

    private static <T> ComponentType<T> register(Identifier id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, id, ((ComponentType.Builder)builderOperator.apply(ComponentType.builder())).build());
    }
}
