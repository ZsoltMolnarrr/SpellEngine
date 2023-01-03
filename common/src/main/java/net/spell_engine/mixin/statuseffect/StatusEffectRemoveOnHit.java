package net.spell_engine.mixin.statuseffect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.status_effect.RemoveOnHitStatusEffect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectRemoveOnHit implements RemoveOnHitStatusEffect {
    @Override
    public boolean shouldRemoveOnDirectHit() {
        return false;
    }
}