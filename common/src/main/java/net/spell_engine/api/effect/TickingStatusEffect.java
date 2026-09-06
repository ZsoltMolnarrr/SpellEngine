package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.SpellTriggers;

public class TickingStatusEffect extends StatusEffect {
    private int interval = 10;

    public TickingStatusEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }

    public TickingStatusEffect interval(int interval) {
        this.interval = interval;
        return this;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity instanceof PlayerEntity player) {
            SpellTriggers.onEffectTick(player, this);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % interval == 0;
    }
}