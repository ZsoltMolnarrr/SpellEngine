package net.spell_engine.compat.rei;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.spellbinding.SpellBinding;

import java.util.List;
import java.util.Optional;

/**
 * REI's view of one thing the Spell Binding Table can do:
 * <ul>
 *     <li>one input: a plain Book becomes a spell book variant (spell book creation)</li>
 *     <li>two inputs: a spell book plus a consumable (lapis or a Spell Scroll of the spell) becomes the same book
 *     with that spell bound</li>
 * </ul>
 * Displays are built on the client by {@link SpellEngineReiClientPlugin} from the synced spell registry, so they
 * never come over REI's display sync. The serializer still exists so REI can persist them (favorites, recipe book).
 * <p>
 * Not client-only: the serializer registry runs on both sides.
 */
public class SpellBindingDisplay extends BasicDisplay {
    public static final CategoryIdentifier<SpellBindingDisplay> CATEGORY =
            CategoryIdentifier.of(SpellEngineMod.ID, SpellBinding.name);

    public static final DisplaySerializer<SpellBindingDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(SpellBindingDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(SpellBindingDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(SpellBindingDisplay::getDisplayLocation)
            ).apply(instance, SpellBindingDisplay::new)),
            PacketCodec.tuple(
                    EntryIngredient.streamCodec().collect(PacketCodecs.toList()), SpellBindingDisplay::getInputEntries,
                    EntryIngredient.streamCodec().collect(PacketCodecs.toList()), SpellBindingDisplay::getOutputEntries,
                    PacketCodecs.optional(Identifier.PACKET_CODEC), SpellBindingDisplay::getDisplayLocation,
                    SpellBindingDisplay::new));

    public SpellBindingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<Identifier> location) {
        super(inputs, outputs, location);
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CATEGORY;
    }

    @Override
    public DisplaySerializer<SpellBindingDisplay> getSerializer() {
        return SERIALIZER;
    }
}
