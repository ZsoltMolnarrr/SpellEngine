package net.combatspells.mixin.client;

import net.combatspells.client.CombatSpellsClient;
import net.combatspells.utils.TargetHelper;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private LivingEntity livingEntity() {
        return (LivingEntity) ((Object)this);
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    public void isGlowing_HEAD(CallbackInfoReturnable<Boolean> cir) {
        if (TargetHelper.isTargetedByClientPlayer(livingEntity()) && CombatSpellsClient.config.highlightTarget) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
