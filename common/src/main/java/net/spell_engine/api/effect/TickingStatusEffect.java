package net.spell_engine.api.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.internals.SpellTriggers;

public class TickingStatusEffect extends MobEffect {
    private int interval = 10;

    public TickingStatusEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public TickingStatusEffect interval(int interval) {
        this.interval = interval;
        return this;
    }

    @Override
    public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            var entry = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this);
            if (entry != null) {
                SpellTriggers.onEffectTick(player, entry);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % interval == 0;
    }
}