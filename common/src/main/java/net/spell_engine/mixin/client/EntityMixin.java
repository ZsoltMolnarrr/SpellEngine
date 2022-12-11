package net.spell_engine.mixin.client;

import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.utils.TargetHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    private Entity entity() {
        return (Entity) ((Object)this);
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    public void getTeamColorValue_HEAD(CallbackInfoReturnable<Integer> cir) {
        if (TargetHelper.isTargetedByClientPlayer(entity()) && SpellEngineClient.config.useMagicColorForHighlight) {
            var clientPlayer = MinecraftClient.getInstance().player;
            var spell = ((SpellCasterClient) clientPlayer).getCurrentSpell();
            if (spell != null) {
                cir.setReturnValue(spell.school.color());
                cir.cancel();
            }
        }
    }
}
