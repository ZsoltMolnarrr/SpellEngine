package net.spell_engine.mixin.effect;

import net.minecraft.world.effect.MobEffect;
import net.spell_engine.api.effect.Synchronized;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MobEffect.class)
public class StatusEffectSynchronized implements Synchronized {
    private boolean shouldSynchronize_SpellEngine = false;

    @Override
    public boolean shouldSynchronize() {
        return shouldSynchronize_SpellEngine;
    }

    @Override
    public MobEffect setSynchronized(boolean value) {
        shouldSynchronize_SpellEngine = value;
        return (MobEffect) ((Object)this);
    }
}
