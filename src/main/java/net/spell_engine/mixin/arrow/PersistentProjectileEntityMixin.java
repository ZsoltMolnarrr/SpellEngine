package net.spell_engine.mixin.arrow;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.arrow.ArrowPerkAdjustable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowPerkAdjustable {
    private boolean arrowPerksAlreadyApplied = false;
    private boolean bypassIFrames = false;

    private PersistentProjectileEntity arrow() {
         return (PersistentProjectileEntity)(Object)this;
    }

    @Override
    public void applyArrowPerks(Spell.ArrowPerks arrowPerks) {
        if(arrowPerksAlreadyApplied || arrowPerks == null) {
            return;
        }
        var arrow = arrow();
        if (arrowPerks.velocity_multiplier != 1.0F) {
            arrow.setVelocity(arrow.getVelocity().multiply(arrowPerks.velocity_multiplier));
        }
        this.bypassIFrames = arrowPerks.bypass_iframes;
        arrowPerksAlreadyApplied = true;
    }

    private int iframeCache = 0;

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

    // Inject into `onEntityHit` TAIL
    @Inject(method = "onEntityHit", at = @At("TAIL"))
    public void onEntityHit_TAIL_SpellEngine(EntityHitResult entityHitResult, CallbackInfo ci) {
        var entity = entityHitResult.getEntity();
        if (entity != null) {
            if (iframeCache != 0) {
                entity.timeUntilRegen = iframeCache;
            }
        }
    }
}
