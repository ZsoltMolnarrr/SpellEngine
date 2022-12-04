package net.combatspells.mixin;

import net.combatspells.internals.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.client.animation.AnimatablePlayer;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.internals.SpellCasterItemStack;
import net.combatspells.runes.RuneCrafter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.combatspells.api.spell.Spell.Release.Target.Type.BEAM;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin implements SpellCasterEntity, RuneCrafter {
    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }

    @Override
    public Identifier getCurrentSpellId() {
        if (player().isUsingItem()) {
            var itemStack = player().getActiveItem();
            if (itemStack != null) {
                var casterStack = (SpellCasterItemStack) ((Object)itemStack);
                return casterStack.getSpellId();
            }
        }
        return null;
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
        var spell = getCurrentSpell();
        if (spell != null) {
            return SpellHelper.getCastProgress(player(), player().getItemUseTimeLeft(), spell);
        }
        return 0;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL(CallbackInfo ci) {
        lastRuneCrafted += 1;
        if (player().world.isClient) {
            ((AnimatablePlayer)player()).updateCastAnimationsOnTick();
        }
    }

    public boolean isBeaming() {
        return getBeam() != null;
    }

    @Nullable
    public Spell.Release.Target.Beam getBeam() {
        var spell = getCurrentSpell();
        if (spell != null && spell.on_release != null && spell.on_release.target.type == BEAM) {
            return spell.on_release.target.beam;
        }
        return null;
    }

    // MARK: RuneCrafter

    private int lastRuneCrafted = 0;

    @Override
    public void setLastCrafted(int time) {
        lastRuneCrafted = time;
    }

    @Override
    public int getLastCrafted() {
        return lastRuneCrafted;
    }
}
