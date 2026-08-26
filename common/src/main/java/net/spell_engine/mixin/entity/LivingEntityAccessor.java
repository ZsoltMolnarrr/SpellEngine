package net.spell_engine.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastHurt")
    float spellEngine_getLastDamageTaken();
    @Accessor("lastDamageSource")
    DamageSource spellEngine_getLastDamageSource();
//    @Accessor
//    int getLastAttackedTicks();
//    @Accessor("lastAttackTime")
//    void setLastAttackedTicks(int lastAttackedTicks);
    @Invoker("tickHeadTurn")
    void spellEngine_invoke_TurnHead(float bodyRotation);

    @Accessor("lastHurtMobTimestamp")
    int spellEngine_getLastAttackedTicks();
    @Accessor("lastHurtMobTimestamp")
    void spellEngine_setLastAttackedTicks(int lastAttackedTicks);
}
