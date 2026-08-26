package net.spell_engine.api.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/// Bleed: a lethal, movement-scaled damage-over-time effect, meant as a physical counterpart to poison.
///
/// It ticks on poison's cadence and uses poison's per-stack base damage (see
/// {@link net.spell_engine.mixin.effect.PoisonEffectMixin}), so its output is expressed relative to
/// poison, and differs on two axes:
/// - **Lethal**: unlike poison there is no `health > 1` floor, so Bleed can finish a target.
/// - **Movement-scaled**: a motionless target bleeds for {@link #MIN_MULTIPLIER} of poison's damage;
///   a target moving at or above {@link #MOVEMENT_SPEED_CAP} bleeds for {@link #MAX_MULTIPLIER}.
public class BleedStatusEffect extends MobEffect {
    /// Poison's per-tick, per-stack damage (from `PoisonEffectMixin`). Bleed scales relative to this.
    private static final float POISON_DAMAGE_PER_STACK = 1.0F;
    /// Tick cadence, in ticks. Matches `PoisonEffectMixin` so "half / double of poison" is per-tick.
    private static final int TICK_INTERVAL = 25;
    /// Damage multiplier while standing still (half of poison).
    private static final double MIN_MULTIPLIER = 0.5;
    /// Damage multiplier at or above the movement cap (double poison).
    private static final double MAX_MULTIPLIER = 2.0;
    /// Horizontal speed, in blocks per second, at which the multiplier reaches {@link #MAX_MULTIPLIER}.
    private static final double MOVEMENT_SPEED_CAP = 2.0;

    public BleedStatusEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICK_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
        // `getVelocity()` is server-authoritative for AI-driven mobs, the usual bleed victims.
        // `* 20` converts blocks/tick to blocks/second, the unit of MOVEMENT_SPEED_CAP.
        var speed = entity.getDeltaMovement().horizontalDistance() * 20.0;
        var fraction = Math.min(speed, MOVEMENT_SPEED_CAP) / MOVEMENT_SPEED_CAP;
        var multiplier = Mth.lerp(fraction, MIN_MULTIPLIER, MAX_MULTIPLIER);
        var stacks = amplifier + 1;
        var damage = (float) (POISON_DAMAGE_PER_STACK * stacks * multiplier);
        // Lethal by design: no `health > 1` floor and no non-lethal cap, unlike poison.
        entity.hurtServer(world, entity.damageSources().magic(), damage);
        return true;
    }
}
