package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)V")
    )
    private void damage_ApplyDamage_entity(
            // Mixin parameters
            LivingEntity instance, ServerWorld world, DamageSource source, float amount, Operation<Void> original
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

        // A fatal-damage (PRE) trigger fired above may have just granted immunity against this hit —
        // e.g. a reactive "cheat death" that applies invulnerability the instant a blow would be
        // lethal. `damage()` already checked `isInvulnerableTo` at its top, before this amount (and
        // thus its fatality) was known, so re-check now: if the entity became immune to this source,
        // cancel the current hit instead of applying it. Without this, such effects only protect
        // against subsequent hits, never the triggering blow.
        if (instance.isInvulnerableTo(world, source)) {
            return; // skip applyDamage: this damage instance is cancelled
        }

        original.call(instance, world, source, amount);
    }
}
