package net.spell_engine.mixin.status_effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.status_effect.RemoveOnHit;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectRemoveOnHit implements RemoveOnHit {
    private boolean SpellEngine_isRemovedOnDirectHit = false;

    @Override
    public boolean shouldRemoveOnDirectHit() {
        return SpellEngine_isRemovedOnDirectHit;
    }

    @Override
    public StatusEffect removedOnDirectHit(boolean value) {
        SpellEngine_isRemovedOnDirectHit = value;
        return (StatusEffect)((Object)this);
    }
}
