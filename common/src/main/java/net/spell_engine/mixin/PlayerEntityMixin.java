package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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

    private final SpellCooldownManager spellCooldownManager = new SpellCooldownManager(player());
    private static final TrackedData<Integer> SPELL_ENGINE_SELECTED_SPELL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(CallbackInfo ci) {
        player().getDataTracker().startTracking(SPELL_ENGINE_SELECTED_SPELL, 0);
    }

//    private Identifier currentSpell;

    public void setCurrentSpellId(Identifier spellId) {
        player().getDataTracker().set(SPELL_ENGINE_SELECTED_SPELL, spellId != null ? SpellRegistry.rawId(spellId) : 0);
    }

    @Override
    public Identifier getCurrentSpellId() {
        var player = player();
        if (player.isUsingItem()) {
            var value = player.getDataTracker().get(SPELL_ENGINE_SELECTED_SPELL);
            if (value != 0) {
                return SpellRegistry.fromRawId(value).orElse(null);
            }
        }
        return null;
    }

    @Override
    public Spell getCurrentSpell() {
        var id = getCurrentSpellId();
        if (id != null) {
            return SpellRegistry.getSpell(id);
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
//            if (!player.isUsingItem() && currentSpell != null) {
//            }
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
