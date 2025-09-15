package net.spell_engine.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor
    float getLastDamageTaken();
    @Accessor
    DamageSource getLastDamageSource();
    @Accessor
    int getLastAttackedTicks();
    @Accessor("lastAttackedTicks")
    void setLastAttackedTicks(int lastAttackedTicks);
    @Invoker("turnHead")
    float invokeTurnHead(float bodyRotation, float headRotation);
}
