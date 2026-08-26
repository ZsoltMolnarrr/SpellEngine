package net.spell_engine.mixin.effect;

import net.minecraft.world.effect.MobEffect;
import net.spell_engine.api.effect.KnockbackImmunity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MobEffect.class)
public class StatusEffectKnockbackImmunity implements KnockbackImmunity {
    private boolean immuneToKnockback = false;

    @Override
    public boolean immuneToKnockback() {
        return immuneToKnockback;
    }

    @Override
    public MobEffect setImmuneToKnockback(boolean value) {
        immuneToKnockback = value;
        return (MobEffect) (Object) this;
    }
}
