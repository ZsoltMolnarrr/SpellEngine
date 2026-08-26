package net.spell_engine.api.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.spell_engine.client.util.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpellEngineEffects {
    public static final List<Effects.Entry> entries = new ArrayList<>();
    private static Effects.Entry add(Effects.Entry entry) {
        entries.add(entry);
        return entry;
    }

    public static Effects.Entry STUN = add(new Effects.Entry(Identifier.fromNamespaceAndPath(SpellEngineMod.ID,"stun"),
            "Stunned",
            "Cannot move or act.",
            new CustomStatusEffect(MobEffectCategory.HARMFUL, 0x888800),
            new EffectConfig(List.of(
                    new AttributeModifier(
                            Attributes.JUMP_STRENGTH.getRegisteredName(),
                            0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    )
            ))
    ));

    public static Effects.Entry IMMOBILIZE = add(new Effects.Entry(Identifier.fromNamespaceAndPath(SpellEngineMod.ID,"immobilize"),
            "Immobilized",
            "Cannot move or jump.",
            new CustomStatusEffect(MobEffectCategory.HARMFUL, 0xcc0000),
            new EffectConfig(List.of(
                    new AttributeModifier(
                            Attributes.JUMP_STRENGTH.getRegisteredName(),
                            -10,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ),
                    new AttributeModifier(
                            Attributes.MOVEMENT_SPEED.getRegisteredName(),
                            -10,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    )
            ))
    ));

    public static Effects.Entry BLEED = add(new Effects.Entry(Identifier.fromNamespaceAndPath(SpellEngineMod.ID,"bleed"),
            "Bleed",
            "Losing health over time, worse while moving.",
            new BleedStatusEffect(MobEffectCategory.HARMFUL, 0xb30000)
    ));

    public static Effects.Entry ENERGY = add(new Effects.Entry(Identifier.fromNamespaceAndPath(SpellEngineMod.ID,"energy"),
            "Energy",
            "The held weapon burns with energy.",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0xffffcc)
    ));

    /// Stacks reach full opacity at amplifier 9, the tenth stack
    private static final float ENERGY_OPACITY_PER_STACK = 1F / 10F;

    public static void register() {
        ActionImpairing.configure(STUN.effect, EntityActionsAllowed.STUN);
        GlowingItemStatusEffect.register(ENERGY.effect, Color.HOLY, ENERGY_OPACITY_PER_STACK);

        for (var entry: entries) {
            Synchronized.configure(entry.effect, true);
        }

        Effects.register(entries, new HashMap<>());
    }
}

