package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.RemoveOnHit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(StatusEffect.class)
public class StatusEffectRemoveOnHit implements RemoveOnHit {
    @Unique
    private RemoveOnHit.Args SpellEngine_removalArgs = null;

    @Override
    public RemoveOnHit.Args getRemovalOnHit() {
        return SpellEngine_removalArgs;
    }

    @Override
    public StatusEffect setRemovalOnHit(Args args) {
        SpellEngine_removalArgs = args;
        return (StatusEffect)((Object)this);
    }
}
