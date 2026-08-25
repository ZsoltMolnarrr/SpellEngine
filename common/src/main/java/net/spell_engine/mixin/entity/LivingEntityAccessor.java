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
//    @Accessor("lastAttackTime")
//    void setLastAttackedTicks(int lastAttackedTicks);
    @Invoker("turnHead")
    void spellEngine_invoke_TurnHead(float bodyRotation);

    @Accessor("lastAttackTime")
    int spellEngine_getLastAttackedTicks();
    @Accessor("lastAttackTime")
    void spellEngine_setLastAttackedTicks(int lastAttackedTicks);
}
