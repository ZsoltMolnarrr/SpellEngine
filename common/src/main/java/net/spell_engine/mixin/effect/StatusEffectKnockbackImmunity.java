package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.KnockbackImmunity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectKnockbackImmunity implements KnockbackImmunity {
    private boolean immuneToKnockback = false;

    @Override
    public boolean immuneToKnockback() {
        return immuneToKnockback;
    }

    @Override
    public StatusEffect setImmuneToKnockback(boolean value) {
        immuneToKnockback = value;
        return (StatusEffect) (Object) this;
    }
}
