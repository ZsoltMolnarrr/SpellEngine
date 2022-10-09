package net.combatspells.mixin;

import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.client.animation.AnimatablePlayer;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.internals.SpellCasterItemStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin implements SpellCasterEntity {
    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }
    @Override
    public Spell getCurrentSpell() {
        if (player().isUsingItem()) {
            var itemStack = player().getActiveItem();
            if (itemStack != null) {
                var casterStack = (SpellCasterItemStack) ((Object)itemStack);
                return casterStack.getSpell();
            }
        }
        return null;
    }

    @Override
    public float getCurrentCastProgress() {
        if (player().isUsingItem()) {
            var spell = getCurrentSpell();
            return SpellHelper.getCastProgress(player(), player().getItemUseTimeLeft(), spell.cast.duration);
        }
        return 0;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void post_Tick(CallbackInfo ci) {
        if (player().world.isClient) {
            ((AnimatablePlayer)player()).updateCastAnimationsOnTick();
        }
    }
}
