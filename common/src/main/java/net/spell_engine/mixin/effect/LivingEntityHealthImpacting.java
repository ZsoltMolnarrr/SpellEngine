package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.entity.SpellEngineAttributes;
import net.spell_engine.api.event.CombatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthImpacting {
    @Shadow public abstract double getAttributeValue(RegistryEntry<EntityAttribute> attribute);

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float modifyHealingTaken_SpellEngine(float amount) {
        return amount * (float) SpellEngineAttributes.HEALING_TAKEN
                .asMultiplier(getAttributeValue(SpellEngineAttributes.HEALING_TAKEN.entry));
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    public float modifyDamageTaken_SpellEngine(float amount) {
        return amount * (float) SpellEngineAttributes.DAMAGE_TAKEN
                .asMultiplier(getAttributeValue(SpellEngineAttributes.DAMAGE_TAKEN.entry));
    }

    @WrapOperation(
            method = "damage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V")
    )
    private void damage_ApplyDamage_entity(
            // Mixin parameters
            LivingEntity instance, DamageSource source, float amount, Operation<Void> original
    ) {
        if (CombatEvents.ENTITY_DAMAGE_INCOMING.isListened()) {
            var args = new CombatEvents.EntityDamageTaken.Args(instance, source, amount);
            CombatEvents.ENTITY_DAMAGE_INCOMING.invoke(listener -> listener.onDamageTaken(args));
        }
        if (instance instanceof PlayerEntity player) {
            if (CombatEvents.PLAYER_DAMAGE_INCOMING.isListened()) {
                var args = new CombatEvents.PlayerDamageTaken.Args(player, source, amount);
                CombatEvents.PLAYER_DAMAGE_INCOMING.invoke(listener -> listener.onPlayerDamageTaken(args));
            }
        }

        original.call(instance, source, amount);
    }
}
