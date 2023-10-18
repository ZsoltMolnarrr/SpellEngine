package net.spell_engine.mixin.arrow;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.spell_engine.particle.ParticleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowExtension {
    private boolean arrowPerksAlreadyApplied = false;
    private boolean bypassIFrames = false;
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
            this.bypassIFrames = perks.bypass_iframes;
            arrowPerksAlreadyApplied = true;
        }
        this.spellId = spellInfo.id();
        arrow.getDataTracker().set(SPELL_ID_TRACKER, SpellRegistry.rawSpellId(spellInfo.id()));
    }

    // MARK: Apply impact effects

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    public void onEntityHit_HEAD_SpellEngine(EntityHitResult entityHitResult, CallbackInfo ci) {
        var entity = entityHitResult.getEntity();
        if (entity != null) {
            if (bypassIFrames) {
                iframeCache = entity.timeUntilRegen;
                entity.timeUntilRegen = 0;
            }
        }
    }
    private int iframeCache = 0;

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    public void onEntityHit_TAIL_SpellEngine(EntityHitResult entityHitResult, CallbackInfo ci) {
        var entity = entityHitResult.getEntity();
        if (entity != null) {
            if (iframeCache != 0) {
                entity.timeUntilRegen = iframeCache;
            }

            var spell = spell();
            var arrow = arrow();
            var owner = arrow.getOwner();
            if (spell != null
                    && spell.impact != null
                    && owner instanceof LivingEntity shooter) {
                SpellHelper.projectileImpact(shooter, arrow, entity, spell,
                        new SpellHelper.ImpactContext().position(entityHitResult.getPos()));
            }
        }
    }
}
