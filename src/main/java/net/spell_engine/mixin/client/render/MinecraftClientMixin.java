package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Inject(method = "hasOutline", at = @At(value = "HEAD"), cancellable = true)
    private void hasOutline_HEAD_SpellEngine(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if(TargetHelper.isTargetedByPlayer(entity, player) && SpellEngineClient.config.highlightTarget) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick_HEAD_SpellEngine(CallbackInfo ci) {
        HudMessages.INSTANCE.tick();
    }
}
