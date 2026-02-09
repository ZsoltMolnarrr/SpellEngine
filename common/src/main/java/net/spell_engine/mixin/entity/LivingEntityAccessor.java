package net.spell_engine.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastDamageTaken")
    float spellEngine_getLastDamageTaken();
    @Accessor("lastDamageSource")
    DamageSource spellEngine_getLastDamageSource();
//    @Accessor
//    int getLastAttackedTicks();
//    @Accessor("lastAttackedTicks")
//    void setLastAttackedTicks(int lastAttackedTicks);
    @Invoker("turnHead")
    float spellEngine_invoke_TurnHead(float bodyRotation, float headRotation);

    @Accessor("lastAttackedTicks")
    int spellEngine_getLastAttackedTicks();
    @Accessor("lastAttackedTicks")
    void spellEngine_setLastAttackedTicks(int lastAttackedTicks);
}
