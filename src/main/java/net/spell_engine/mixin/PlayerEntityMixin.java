package net.spell_engine.mixin;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.internals.*;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
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

    private SpellCast.Process serverSide_SpellCastProcess = null;
    public void setSpellCastProcess(@Nullable SpellCast.Process process) {
        serverSide_SpellCastProcess = process;
        Identifier spellId = null;
        if (process != null) {
            spellId = process.id();
        }
        player().getDataTracker().set(SPELL_ENGINE_SELECTED_SPELL, spellId != null ? SpellRegistry.rawSpellId(spellId) : 0);
    }

    @Nullable public SpellCast.Process getSpellCastProcess() {
        return serverSide_SpellCastProcess;
    }

    public void clearCasting() { // TODO: Most likely remove
        serverSide_SpellCastProcess = null;
    }

    @Override
    public Identifier getCurrentSpellId() {
        var player = player();
        if (player.isUsingItem()) {
            var value = player.getDataTracker().get(SPELL_ENGINE_SELECTED_SPELL);
            if (value != 0) {
                return SpellRegistry.fromRawSpellId(value).orElse(null);
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
    public SpellCooldownManager getCooldownManager() {
        return spellCooldownManager;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        if (player.getWorld().isClient) {
            ((AnimatablePlayer)player()).updateSpellCastAnimationsOnTick();
//            if (!player.isUsingItem() && currentSpell != null) {
//            }
        } else {
            // Server side
            if (!player.isUsingItem() || SpellContainerHelper.containerWithProxy(player.getActiveItem(), player) == null) {
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
