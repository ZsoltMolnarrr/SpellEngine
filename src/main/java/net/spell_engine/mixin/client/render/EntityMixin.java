package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.utils.TargetHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void getTeamColorValue_HEAD_SpellEngine(CallbackInfoReturnable<Integer> cir) {
        var entity = (Entity) ((Object)this);
        if (entity.getWorld().isClient /* && SpellEngineClient.config.useMagicColorForHighlight */) {
            var clientPlayer = MinecraftClient.getInstance().player;
            if (TargetHelper.isTargetedByPlayer(entity, clientPlayer)) {
                var spell = ((SpellCasterClient) clientPlayer).getCurrentSpell();
                if (spell != null) {
                    cir.setReturnValue(spell.school.color());
                    cir.cancel();
                }
            }
        }
    }
}
