package net.spell_engine.mixin.registry;

import net.minecraft.core.component.DataComponents;
import net.spell_engine.api.spell.SpellDataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DataComponents.class)
public class DataComponentTypesMixin {

    // Static class init tail inject

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void static_init_TAIL_SpellEngine(CallbackInfo ci) {
        var asd = SpellDataComponents.SPELL_CONTAINER;// Initialize the component type
    }
}
