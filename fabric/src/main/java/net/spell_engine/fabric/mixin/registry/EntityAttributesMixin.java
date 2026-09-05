package net.spell_engine.fabric.mixin.registry;

import net.minecraft.entity.attribute.EntityAttributes;
import net.spell_engine.api.entity.SpellEngineAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Fabric-only registration hook: registers Spell Engine's attributes right after vanilla's, so
/// they exist before any `DefaultAttributeContainer` is built. Forge must NOT ship this mixin —
/// there the same idempotent `SpellEngineAttributes.register()` runs from `RegisterEvent`
/// (`RegistryKeys.ATTRIBUTE`) in the Forge entrypoint.
@Mixin(EntityAttributes.class)
public class EntityAttributesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void static_tail_SpellEngine(CallbackInfo ci) {
        SpellEngineAttributes.register();
    }
}
