package net.spell_engine.mixin.arrow;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.entity.SummonedEntity;
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowExtension {
    @Shadow protected boolean inGround;

    @Shadow public abstract byte getPierceLevel();

    @Shadow public abstract void setPierceLevel(byte level);

    @Shadow public abstract void setDamage(double damage);

    @Shadow public abstract double getDamage();

    private PersistentProjectileEntity arrow() {
         return (PersistentProjectileEntity)(Object)this;
    }

    private final List<Identifier> spellIds = new ArrayList<>();
    private void addSpellId(Identifier id) {
        if (!spellIds.contains(id)) {
            spellIds.add(id);
        }
        var stringList = this.spellIds.stream().map(Identifier::toString).toList();
        var json = gson.toJson(stringList);
        arrow().getDataTracker().set(SPELL_ID_TRACKER, json);
    }

    private List<RegistryEntry<Spell>> cachedSpellEntry = List.of();
    @Nullable List<RegistryEntry<Spell>> spellEntries() {
        if (cachedSpellEntry == null || cachedSpellEntry.size() != spellIds.size()) {
            var entries = spellIds.stream()
                    .map(id -> {
                        var reference = SpellRegistry.from(arrow().getWorld()).getEntry(id).orElse(null);
                        return (RegistryEntry<Spell>)reference;
                    })
                    .toList();
            cachedSpellEntry = entries;
        }
        return cachedSpellEntry;
    }

    private boolean arrowPerksAlreadyApplied(RegistryEntry<Spell> spell) {
        var id = spell.getKey().get().getValue().toString();
        return spellIds.contains(id);
    }

    // MARK: Persist extra data

    private static final Gson gson = new Gson();
    private static final Type stringListType = new TypeToken<ArrayList<String>>(){}.getType();
    private static final String NBT_KEY_SPELL_ID = "spell_id";

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void writeCustomDataToNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        var stringList = this.spellIds.stream().map(Identifier::toString).toList();
        var json = gson.toJson(stringList);
        nbt.putString(NBT_KEY_SPELL_ID, json);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void readCustomDataFromNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(NBT_KEY_SPELL_ID)) {
            var string = nbt.getString(NBT_KEY_SPELL_ID);
            if (string != null && !string.isEmpty()) {
                List<String> stringList = new Gson().fromJson(string, stringListType);
                this.spellIds.clear();
                for (var idString : stringList) {
                    var id = Identifier.tryParse(idString);
                    if (id != null) {
                        addSpellId(Identifier.of(idString));
                    }
                }
            }
        }
    }

    // MARK: Sync data to client

    private static final TrackedData<String> SPELL_ID_TRACKER = DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.STRING);
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SPELL_ID_TRACKER, "");
    }

    // MARK: Tick

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick_HEAD_SpellEngine(CallbackInfo ci) {
        var arrow = arrow();
        var world = arrow.getWorld();
        if (world.isClient && this.spellIds.isEmpty()) {
            var json = arrow().getDataTracker().get(SPELL_ID_TRACKER);
            if (json.isEmpty()) {
                return;
            }
            try {
                List<String> stringList = new Gson().fromJson(json, stringListType);
                this.spellIds.clear();
                this.spellIds.addAll(stringList.stream().map(Identifier::of).toList());
                this.spellEntries();
            } catch (Exception e) {
                System.err.println("Spell Engine: Failed to parse spell id from arrow data tracker: " + json);
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(CallbackInfo ci) {
        for (var spellEntry : spellEntries()) {
            var spell = spellEntry.value();
            var perks = spell.arrow_perks;
            if (perks != null && perks.travel_particles != null) {
                var arrow = arrow();
                for (var travel_particles : perks.travel_particles) {
                    ParticleHelper.play(arrow.getWorld(), arrow, arrow.getYaw(), arrow.getPitch(), travel_particles);
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
        return inGround;
    }

    @Nullable public List<RegistryEntry<Spell>> getCarriedSpells() {
        return spellEntries();
    }

    @Override
    public void applyArrowPerks(RegistryEntry<Spell> spellEntry, Spell.ArrowPerks perks) {
        if(arrowPerksAlreadyApplied(spellEntry)) {
            return;
        }
        var arrow = arrow();
        if (perks != null) {
            if (perks.velocity_multiplier != 1.0F) {
                arrow.setVelocity(arrow.getVelocity().multiply(perks.velocity_multiplier));
            }
            if (perks.pierce > 0) {
                var newPierce = (byte)(getPierceLevel() + perks.pierce);
                setPierceLevel(newPierce);
            }
            this.setDamage(this.getDamage() * perks.damage_multiplier);
        }
        var spellId = spellEntry.getKey().get().getValue();
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
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void canHit_HEAD_SpellEngine(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof SummonedEntity summon)) return;
        var owner = arrow().getOwner();
        if (owner == null) return;
        // Own summon: matched by owner UUID rather than by relation, so the shooter's arrows keep
        // passing through their own pets even on a server that configures `player_relation_to_owned_pets`
        // as something other than ALLY (the config comment invites exactly that, to allow pet friendly fire).
        if (owner.getUuid().equals(summon.getOwnerUuid())) {
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

    @Inject(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"), cancellable = true)
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

    @WrapOperation(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean wrapDamageEntity(
            // Mixin Parameters
            Entity entity, DamageSource damageSource, float amount, Operation<Boolean> original,
            // Context Parameters
            EntityHitResult entityHitResult) {
        var spellEntries = spellEntries();
        var arrow = arrow();
        var owner = arrow.getOwner();
        if (entity.getWorld().isClient() || spellEntries.isEmpty()) {

            var result = original.call(entity, damageSource, amount);
            if (owner instanceof PlayerEntity shooter) {
                SpellTriggers.onArrowImpact((ArrowExtension) arrow, shooter, entity, damageSource, amount);
            }
            return result;
        } else {
            int iFrameToRestore = 0;
            var originalIFrame = entity.timeUntilRegen;
            float knockbackMultiplier = 1.0F;

            for (var spellEnrty : spellEntries) {
                var spell = spellEnrty.value();
                var arrowPerks = spell.arrow_perks;
                if (arrowPerks != null) {
                    if (arrowPerks.knockback != 1.0F) {
                        knockbackMultiplier *= arrowPerks.knockback;
                    }
                    if (arrowPerks.bypass_iframes) {
                        if (entity.timeUntilRegen == originalIFrame) {
                            iFrameToRestore = entity.timeUntilRegen;
                        }
                        entity.timeUntilRegen = 0;
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
            if (owner instanceof PlayerEntity shooter) {
                SpellTriggers.onArrowImpact((ArrowExtension) arrow, shooter, entity, damageSource, amount);
            }

            if (pushedKnockback) {
                ((ConfigurableKnockback) entity).popKnockbackMultiplier_SpellEngine();
            }
            if (iFrameToRestore != 0) {
                entity.timeUntilRegen = iFrameToRestore;
            }
            return result;
        }
    }

    private void performImpacts(RegistryEntry<Spell> spellEntry, Entity target, EntityHitResult entityHitResult) {
        var arrow = arrow();
        var owner = arrow.getOwner();
        if (spellEntry != null
                && spellEntry.value().impacts != null
                && owner instanceof LivingEntity shooter) {
            SpellImpacts.arrowImpact(shooter, arrow, target, spellEntry,
                    new SpellExecution.ImpactContext().position(entityHitResult.getPos()));
        }
    }
}
