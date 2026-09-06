package net.spell_engine.api.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;
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

    public static Effects.Entry STUN = add(new Effects.Entry(new Identifier(SpellEngineMod.ID,"stun"),
            "Stunned",
            "Cannot move or act.",
            // 1.20.1 has no generic jump-strength attribute; jumping is blocked by ActionImpairing (STUN) instead.
            new CustomStatusEffect(StatusEffectCategory.HARMFUL, 0x888800)
    ));

    public static Effects.Entry IMMOBILIZE = add(new Effects.Entry(new Identifier(SpellEngineMod.ID,"immobilize"),
            "Immobilized",
            "Cannot move or jump.",
            new CustomStatusEffect(StatusEffectCategory.HARMFUL, 0xcc0000),
            // 1.20.1 has no generic jump-strength attribute; only movement speed is zeroed here.
            new EffectConfig(List.of(
                    new AttributeModifier(
                            "minecraft:generic.movement_speed",
                            -10,
                            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
                    )
            ))
    ));

    public static Effects.Entry BLEED = add(new Effects.Entry(new Identifier(SpellEngineMod.ID,"bleed"),
            "Bleed",
            "Losing health over time, worse while moving.",
            new BleedStatusEffect(StatusEffectCategory.HARMFUL, 0xb30000)
    ));

    public static Effects.Entry ENERGY = add(new Effects.Entry(new Identifier(SpellEngineMod.ID,"energy"),
            "Energy",
            "The held weapon burns with energy.",
            new CustomStatusEffect(StatusEffectCategory.BENEFICIAL, 0xffffcc)
    ));

    /// Stacks reach full opacity at amplifier 9, the tenth stack
    private static final float ENERGY_OPACITY_PER_STACK = 1F / 10F;

    private static boolean configured = false;

    /// Idempotent. Fabric: call from mod init; Forge: call from the `STATUS_EFFECT` `RegisterEvent`.
    public static void register() {
        if (!configured) {
            configured = true;
            ActionImpairing.configure(STUN.effect, EntityActionsAllowed.STUN);
            // Replaces the 1.21 jump-strength attribute modifier (absent on 1.20.1): block jumping only.
            ActionImpairing.configure(IMMOBILIZE.effect, new EntityActionsAllowed(false, true,
                    new EntityActionsAllowed.PlayersAllowed(true, true, true),
                    new EntityActionsAllowed.MobsAllowed(true),
                    EntityActionsAllowed.SemanticType.NONE));
            GlowingItemStatusEffect.register(ENERGY.effect, Color.HOLY, ENERGY_OPACITY_PER_STACK);
            for (var entry: entries) {
                Synchronized.configure(entry.effect, true);
            }
        }
        Effects.register(entries, new HashMap<>());
    }
}

