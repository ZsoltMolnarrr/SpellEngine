package net.spell_engine.mixin.client.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.client.input.SpellHotbar;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class SpellHotbarMinecraftClient {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        SpellHotbar.INSTANCE.update(player);
    }

    @Inject(method = "handleInputEvents", at = @At(value = "HEAD"))
    private void handleInputEvents_HEAD_SpellHotbar(CallbackInfo ci) {
        if (player == null) { return; }
        SpellHotbar.INSTANCE.handle(player);
    }
}
