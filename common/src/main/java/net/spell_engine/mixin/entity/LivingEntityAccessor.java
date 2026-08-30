package net.spell_engine.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastHurt")
    float spellEngine_getLastDamageTaken();

    @Invoker("tickHeadTurn")
    void spellEngine_invoke_TurnHead(float bodyRotation);

    /// Read side is vanilla's public `getLastHurtMobTimestamp()`; there is no vanilla setter
    /// (`setLastHurtMob` also writes `lastHurtByMob`), hence this one.
    @Accessor("lastHurtMobTimestamp")
    void spellEngine_setLastAttackedTicks(int lastAttackedTicks);
}
