package net.spell_engine.mixin.registry;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.spell_engine.api.entity.SpellEngineAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Attributes.class)
public class EntityAttributesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void static_tail_RangedWeaponAPI(CallbackInfo ci) {
        SpellEngineAttributes.register();
    }
}
