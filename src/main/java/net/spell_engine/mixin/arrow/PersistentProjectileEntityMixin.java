package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.spell_engine.particle.ParticleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowExtension {
    @Shadow protected boolean inGround;

    @Shadow public abstract byte getPierceLevel();

    @Shadow public abstract void setPierceLevel(byte level);

    @Shadow public abstract void setDamage(double damage);

    @Shadow public abstract double getDamage();

    private boolean arrowPerksAlreadyApplied = false;
    private Identifier spellId = null;

    private PersistentProjectileEntity arrow() {
         return (PersistentProjectileEntity)(Object)this;
    }

    @Nullable Spell spell() {
        if (spellId != null) {
            return SpellRegistry.getSpell(spellId);
        }
        return null;
    }

    // MARK: Persist extra data

    private static final String NBT_KEY_SPELL_ID = "spell_id";

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void writeCustomDataToNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        if (spellId != null) {
            nbt.putString(NBT_KEY_SPELL_ID, spellId.toString());
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void readCustomDataFromNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(NBT_KEY_SPELL_ID)) {
            var string = nbt.getString(NBT_KEY_SPELL_ID);
            if (string != null && !string.isEmpty()) {
                spellId = new Identifier(nbt.getString(NBT_KEY_SPELL_ID));
            }
        }
    }

    // MARK: Sync data to client

    private static final TrackedData<Integer> SPELL_ID_TRACKER = DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine(CallbackInfo ci) {
        arrow().getDataTracker().startTracking(SPELL_ID_TRACKER, 0);
    }

    // MARK: Tick

    private int client_lastResolvedSpellRawId = 0;
    @Nullable private Spell client_lastResolvedSpell = null;
    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellEngine(CallbackInfo ci) {
        var arrow = arrow();
        if (arrow.getWorld().isClient) {
            var rawId = arrow().getDataTracker().get(SPELL_ID_TRACKER);
            if (rawId != client_lastResolvedSpellRawId) {
                client_lastResolvedSpellRawId = rawId;
                spellId = SpellRegistry.fromRawSpellId(rawId).orElse(null);
                client_lastResolvedSpell = spell();
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(CallbackInfo ci) {
        if (client_lastResolvedSpell != null) {
            var perks = client_lastResolvedSpell.arrow_perks;
            if (perks.travel_particles != null) {
                var arrow = arrow();
                for (var travel_particles : perks.travel_particles) {
                    ParticleHelper.play(arrow.getWorld(), arrow, arrow.getYaw(), arrow.getPitch(), travel_particles);
                }
            }
        }
    }

    // MARK: ArrowExtension

    private boolean allowByPassingIFrames = true; // This doesn't mean it will bypass, arrowPerks also need to allow it
    @Override
    public void allowByPassingIFrames_SpellEngine(boolean allow) {
        allowByPassingIFrames = allow;
    }

    @Override
    public boolean isInGround_SpellEngine() {
        return inGround;
    }

    @Override
    @Nullable public Identifier getCarriedSpellId() {
        return spellId;
    }

    @Nullable public Spell getCarriedSpell() {
        if (arrow().getWorld().isClient()) {
            return client_lastResolvedSpell;
        } else {
            return spell();
        }
    }

    @Override
    public void applyArrowPerks(SpellInfo spellInfo) {
        if(arrowPerksAlreadyApplied) {
            return;
        }
        var arrow = arrow();
        var perks = spellInfo.spell().arrow_perks;
        if (perks != null) {
            if (perks.velocity_multiplier != 1.0F) {
                arrow.setVelocity(arrow.getVelocity().multiply(perks.velocity_multiplier));
            }
            arrowPerksAlreadyApplied = true;
            if (perks.pierce > 0) {
                var newPierce = (byte)(getPierceLevel() + perks.pierce);
                setPierceLevel(newPierce);
            }
            this.setDamage(this.getDamage() * perks.damage_multiplier);
        }
        this.spellId = spellInfo.id();
        arrow.getDataTracker().set(SPELL_ID_TRACKER, SpellRegistry.rawSpellId(spellInfo.id()));
    }

    // MARK: Apply impact effects

    @Inject(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"), cancellable = true)
    private void onEntityHit_BeforeDamage_SpellEngine(EntityHitResult entityHitResult, CallbackInfo ci) {
        var spell = spell();
        if (spell != null) {
            var arrowPerks = spell.arrow_perks;
            if (arrowPerks != null) {
                if (arrowPerks.skip_arrow_damage) {
                    ci.cancel();
                    arrow().discard();
                    var entity = entityHitResult.getEntity();
                    if (entity != null) {
                        performImpacts(entity, entityHitResult);
                    }
                }
            }
        }
    }

    @WrapOperation(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean wrapDamageEntity(
            // Mixin Parameters
            Entity entity, DamageSource damageSource, float amount, Operation<Boolean> original,
            // Context Parameters
            EntityHitResult entityHitResult) {
        var spell = spell();
        if (entity.getWorld().isClient() || spell == null) {
            return original.call(entity, damageSource, amount);
        } else {
            var arrowPerks = spell.arrow_perks;
            var pushedKnockback = false;
            int iFrameToRestore = 0;
            if (entity instanceof LivingEntity livingEntity && arrowPerks != null) {
                if (arrowPerks.knockback != 1.0F) {
                    ((ConfigurableKnockback) livingEntity).pushKnockbackMultiplier_SpellEngine(arrowPerks.knockback);
                    pushedKnockback = true;
                }
                if (allowByPassingIFrames && arrowPerks.bypass_iframes) {
                    iFrameToRestore = entity.timeUntilRegen;
                    entity.timeUntilRegen = 0;
                }
                if (arrowPerks.iframe_to_set > 0) {
                    iFrameToRestore = arrowPerks.iframe_to_set;
                }
            }

            var result = original.call(entity, damageSource, amount);
            performImpacts(entity, entityHitResult);

            if (pushedKnockback) {
                ((ConfigurableKnockback) entity).popKnockbackMultiplier_SpellEngine();
            }
            if (iFrameToRestore != 0) {
                entity.timeUntilRegen = iFrameToRestore;
            }
            return result;
        }
    }

    private void performImpacts(Entity target, EntityHitResult entityHitResult) {
        var spell = spell();
        var arrow = arrow();
        var owner = arrow.getOwner();
        if (spell != null
                && spell.impact != null
                && owner instanceof LivingEntity shooter) {
            SpellHelper.projectileImpact(shooter, arrow, target, spell,
                    new SpellHelper.ImpactContext().position(entityHitResult.getPos()));
        }
    }
}
