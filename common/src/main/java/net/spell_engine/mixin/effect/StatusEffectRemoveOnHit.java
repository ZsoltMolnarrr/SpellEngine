package net.spell_engine.mixin.effect;

import net.minecraft.world.effect.MobEffect;
import net.spell_engine.api.effect.RemoveOnHit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MobEffect.class)
public class StatusEffectRemoveOnHit implements RemoveOnHit {
    @Unique
    private RemoveOnHit.Args SpellEngine_removalArgs = null;

    @Override
    public RemoveOnHit.Args getRemovalOnHit() {
        return SpellEngine_removalArgs;
    }

    @Override
    public MobEffect setRemovalOnHit(Args args) {
        SpellEngine_removalArgs = args;
        return (MobEffect)((Object)this);
    }
}
