package net.spell_engine.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.internals.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.spell_engine.api.spell.Spell.Release.Target.Type.BEAM;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin implements SpellCasterEntity {
    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }

    private Identifier currentSpell;
    private final SpellCooldownManager spellCooldownManager = new SpellCooldownManager(player());

    public void setCurrentSpell(Identifier spellId) {
        currentSpell = spellId;
    }

    @Override
    public Identifier getCurrentSpellId() {
        if (player().isUsingItem()) {
            return currentSpell;
        }
        return null;
    }

    @Override
    public Spell getCurrentSpell() {
        if (player().isUsingItem()) {
            return SpellRegistry.getSpell(currentSpell);
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

    @Override
    public void clearCasting() {
        var player = player();
        if (!player.world.isClient) {
            // Server
            SpellCastSyncHelper.clearCasting(player);
        }
    }

    @Override
    public SpellCooldownManager getCooldownManager() {
        return spellCooldownManager;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        if (player.world.isClient) {
            ((AnimatablePlayer)player()).updateSpellCastAnimationsOnTick();
            if (!player.isUsingItem() && currentSpell != null) {
            }
        } else {
            // Server side
            if (!player.isUsingItem() || SpellContainerHelper.containerFromItemStack(player.getActiveItem()) == null) {
                SpellCastSyncHelper.clearCasting(player);
            }
        }
        spellCooldownManager.update();
    }

    public boolean isBeaming() {
        return getBeam() != null;
    }

    @Nullable
    public Spell.Release.Target.Beam getBeam() {
        var spell = getCurrentSpell();
        if (spell != null && spell.release != null && spell.release.target.type == BEAM) {
            return spell.release.target.beam;
        }
        return null;
    }
}
