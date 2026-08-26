package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.entity.SummonedEntity;
import net.spell_engine.internals.SpellEngineAttachments;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.delivery.arrow.ArrowExtension;
import net.spell_engine.internals.target.EntityRelation;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.fx.ParticleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;

@Mixin(AbstractArrow.class)
public abstract class PersistentProjectileEntityMixin implements ArrowExtension {
    @Shadow public abstract boolean isInGround(); // 1.21.11: tracked-data backed, no field

    @Shadow public abstract byte getPierceLevel();

    @Shadow public abstract void setPierceLevel(byte level);

    @Shadow public abstract void setBaseDamage(double damage);

    @Shadow private double baseDamage; // 1.21.11: no public getter

    private AbstractArrow arrow() {
         return (AbstractArrow)(Object)this;
    }

    // MARK: Carried spells — synced + persisted entity attachment (ARROW_SPELLS)

    private List<Identifier> spellIds() {
        return SpellEngineAttachments.ARROW_SPELLS.get(arrow());
    }

    private void addSpellId(Identifier id) {
        var ids = spellIds();
        if (ids.contains(id)) { return; }
        var updated = new ArrayList<>(ids);
        updated.add(id);
        SpellEngineAttachments.ARROW_SPELLS.set(arrow(), List.copyOf(updated));
    }

    /// Resolved spell entries, re-resolved whenever the carried id list changes (a new synced value
    /// is a new list instance; identity is compared, so steady-state reads are free).
    private List<Identifier> cachedSpellIds = null;
    private List<Holder<Spell>> cachedSpellEntry = List.of();
    @Nullable List<Holder<Spell>> spellEntries() {
        var ids = spellIds();
        if (cachedSpellIds != ids) {
            cachedSpellEntry = ids.stream()
                    .map(id -> {
                        var reference = SpellRegistry.from(arrow().level()).get(id).orElse(null);
                        return (Holder<Spell>)reference;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            cachedSpellIds = ids;
        }
        return cachedSpellEntry;
    }

    private boolean arrowPerksAlreadyApplied(Holder<Spell> spell) {
        return spellIds().contains(spell.unwrapKey().get().identifier());
    }

    // MARK: Tick

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(CallbackInfo ci) {
        for (var spellEntry : spellEntries()) {
            var spell = spellEntry.value();
            var perks = spell.arrow_perks;
            if (perks != null && perks.travel_particles != null) {
                var arrow = arrow();
                for (var travel_particles : perks.travel_particles) {
                    ParticleHelper.play(arrow.level(), arrow, arrow.getYRot(), arrow.getXRot(), travel_particles);
                }
            }
        }
    }

    // MARK: ArrowExtension

//    private boolean allowByPassingIFrames = true; // This doesn't mean it will bypass, arrowPerks also need to allow it
//    @Override
//    public void allowByPassingIFrames_SpellEngine(boolean allow) {
//        allowByPassingIFrames = allow;
//    }

    @Override
    public boolean isInGround_SpellEngine() {
        return isInGround();
    }

    @Nullable public List<Holder<Spell>> getCarriedSpells() {
        return spellEntries();
    }

    @Override
    public void applyArrowPerks(Holder<Spell> spellEntry, Spell.ArrowPerks perks) {
        if(arrowPerksAlreadyApplied(spellEntry)) {
            return;
        }
        var arrow = arrow();
        if (perks != null) {
            if (perks.velocity_multiplier != 1.0F) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(perks.velocity_multiplier));
            }
            if (perks.pierce > 0) {
                var newPierce = (byte)(getPierceLevel() + perks.pierce);
                setPierceLevel(newPierce);
            }
            this.setBaseDamage(this.baseDamage * perks.damage_multiplier);
        }
        var spellId = spellEntry.unwrapKey().get().identifier();
        this.addSpellId(spellId);
    }

    // MARK: Pass through owner's summons

    /// A shooter's arrows pass through summons they have no business hitting, instead of colliding
    /// with them: their own (e.g. an archer's arrows fly through their Spirit Wolf), and any summon
    /// whose relation to the shooter resolves to ALLY or FRIENDLY — an ally's or a teammate's pets.
    /// Returning false here makes the hit raycast skip the summon entirely, so the arrow continues to
    /// targets behind it.
    ///
    /// Summons of HOSTILE / NEUTRAL parties stay hittable, as does any summon whose owner cannot be
    /// resolved (an offline owner falls through to the config's fallback relation). `SummonedEntity`'s
    /// own `canHit` / `is_attackable` gate still applies on top of this.
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void canHit_HEAD_SpellEngine(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof SummonedEntity summon)) return;
        var owner = arrow().getOwner();
        if (owner == null) return;
        // Own summon: matched by owner UUID rather than by relation, so the shooter's arrows keep
        // passing through their own pets even on a server that configures `player_relation_to_owned_pets`
        // as something other than ALLY (the config comment invites exactly that, to allow pet friendly fire).
        if (owner.getUUID().equals(summon.getOwnerUuid())) {
            cir.setReturnValue(false);
            return;
        }
        if (owner instanceof LivingEntity shooter) {
            var relation = EntityRelations.getRelation(shooter, summon);
            if (relation == EntityRelation.ALLY || relation == EntityRelation.FRIENDLY) {
                cir.setReturnValue(false);
            }
        }
    }

    // MARK: Apply impact effects

    @Inject(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), cancellable = true)
    private void onEntityHit_BeforeDamage_SpellEngine(EntityHitResult entityHitResult, CallbackInfo ci) {
        for (var spellEntry : spellEntries()) {
            var spell = spellEntry.value();
            var arrowPerks = spell.arrow_perks;
            if (arrowPerks != null) {
                if (arrowPerks.skip_arrow_damage) {
                    ci.cancel();
                    arrow().discard();
                    var entity = entityHitResult.getEntity();
                    if (entity != null) {
                        performImpacts(spellEntry, entity, entityHitResult);
                    }
                }
            }
        }
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean wrapDamageEntity(
            // Mixin Parameters
            Entity entity, DamageSource damageSource, float amount, Operation<Boolean> original,
            // Context Parameters
            EntityHitResult entityHitResult) {
        var spellEntries = spellEntries();
        var arrow = arrow();
        var owner = arrow.getOwner();
        if (entity.level().isClientSide() || spellEntries.isEmpty()) {

            var result = original.call(entity, damageSource, amount);
            if (owner instanceof Player shooter) {
                SpellTriggers.onArrowImpact((ArrowExtension) arrow, shooter, entity, damageSource, amount);
            }
            return result;
        } else {
            int iFrameToRestore = 0;
            var originalIFrame = entity.invulnerableTime;
            float knockbackMultiplier = 1.0F;

            for (var spellEnrty : spellEntries) {
                var spell = spellEnrty.value();
                var arrowPerks = spell.arrow_perks;
                if (arrowPerks != null) {
                    if (arrowPerks.knockback != 1.0F) {
                        knockbackMultiplier *= arrowPerks.knockback;
                    }
                    if (arrowPerks.bypass_iframes) {
                        if (entity.invulnerableTime == originalIFrame) {
                            iFrameToRestore = entity.invulnerableTime;
                        }
                        entity.invulnerableTime = 0;
                    }
                    if (arrowPerks.iframe_to_set > 0) {
                        iFrameToRestore = arrowPerks.iframe_to_set;
                    }
                }
            }

            var pushedKnockback = false;
            if (entity instanceof LivingEntity livingEntity) {
                if (knockbackMultiplier != 1.0F) {
                    ((ConfigurableKnockback) livingEntity).pushKnockbackMultiplier_SpellEngine(knockbackMultiplier);
                    pushedKnockback = true;
                }
            }

            var result = original.call(entity, damageSource, amount);
            for (var spellEntry : spellEntries) {
                performImpacts(spellEntry, entity, entityHitResult);
            }
            if (owner instanceof Player shooter) {
                SpellTriggers.onArrowImpact((ArrowExtension) arrow, shooter, entity, damageSource, amount);
            }

            if (pushedKnockback) {
                ((ConfigurableKnockback) entity).popKnockbackMultiplier_SpellEngine();
            }
            if (iFrameToRestore != 0) {
                entity.invulnerableTime = iFrameToRestore;
            }
            return result;
        }
    }

    private void performImpacts(Holder<Spell> spellEntry, Entity target, EntityHitResult entityHitResult) {
        var arrow = arrow();
        var owner = arrow.getOwner();
        if (spellEntry != null
                && spellEntry.value().impacts != null
                && owner instanceof LivingEntity shooter) {
            SpellImpacts.arrowImpact(shooter, arrow, target, spellEntry,
                    new SpellExecution.ImpactContext().position(entityHitResult.getLocation()));
        }
    }
}
