package net.spell_engine.api.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.config.AttributeModifier;
import net.spell_engine.api.config.EffectConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpellEngineEffects {
    public static final List<Effects.Entry> entries = new ArrayList<>();
    private static Effects.Entry add(Effects.Entry entry) {
        entries.add(entry);
        return entry;
    }

    public static Effects.Entry STUN = add(new Effects.Entry(Identifier.of(SpellEngineMod.ID,"stun"),
            "Stunned",
            "Cannot move or act.",
            new CustomStatusEffect(StatusEffectCategory.HARMFUL, 0x888800),
            new EffectConfig(List.of(
                    new AttributeModifier(
                            EntityAttributes.GENERIC_JUMP_STRENGTH.getIdAsString(),
                            0,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    )
            ))
    ));

    public static Effects.Entry IMMOBILIZE = add(new Effects.Entry(Identifier.of(SpellEngineMod.ID,"immobilize"),
            "Immobilized",
            "Cannot move or jump.",
            new CustomStatusEffect(StatusEffectCategory.HARMFUL, 0xcc0000),
            new EffectConfig(List.of(
                    new AttributeModifier(
                            EntityAttributes.GENERIC_JUMP_STRENGTH.getIdAsString(),
                            -10,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ),
                    new AttributeModifier(
                            EntityAttributes.GENERIC_MOVEMENT_SPEED.getIdAsString(),
                            -10,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    )
            ))
    ));

    public static void register() {
        ActionImpairing.configure(STUN.effect, EntityActionsAllowed.STUN);

        for (var entry: entries) {
            Synchronized.configure(entry.effect, true);
        }

        Effects.register(entries, new HashMap<>());
    }
}

