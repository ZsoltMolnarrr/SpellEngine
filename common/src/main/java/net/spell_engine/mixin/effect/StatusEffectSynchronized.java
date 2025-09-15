package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.Synchronized;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectSynchronized implements Synchronized {
    private boolean shouldSynchronize_SpellEngine = false;

    @Override
    public boolean shouldSynchronize() {
        return shouldSynchronize_SpellEngine;
    }

    @Override
    public StatusEffect setSynchronized(boolean value) {
        shouldSynchronize_SpellEngine = value;
        return (StatusEffect) ((Object)this);
    }
}
