package net.spell_engine.mixin.effect;

import net.minecraft.entity.Entity;
import net.spell_engine.api.effect.EntityImmunity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;

@Mixin(Entity.class)
public class EntityImmunityMixin implements EntityImmunity {
    private static HashMap<EntityImmunity.Type, Integer> immunityDefaults() {
        var map = new HashMap<Type, Integer>();
        for (var type: EntityImmunity.Type.values()) {
            map.put(type, 0);
        }
        return map;
    }
    private final HashMap<EntityImmunity.Type, Integer> immunity = immunityDefaults();

    public boolean isImmuneTo(Type type) {
        return immunity.get(type) > 0;
    }

    @Override
    public void setImmuneTo(Type type, int ticks) {
        immunity.put(type, ticks);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_Immunity(CallbackInfo ci) {
        for (var type: EntityImmunity.Type.values()) {
            var ticks = immunity.get(type);
            if (ticks > 0) {
                immunity.put(type, ticks - 1);
            }
        }
    }

    @Inject(method = "isImmuneToExplosion", at = @At("HEAD"), cancellable = true)
    private void isImmuneToExplosion(CallbackInfoReturnable<Boolean> cir) {
        if (isImmuneTo(Type.EXPLOSION)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
