package net.spell_engine.mixin.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.internals.SpellEngineAttachments;
import net.spell_engine.internals.delivery.arrow.ArrowShootContext;
import net.spell_engine.internals.casting.SpellCastInteractor;
import net.spell_engine.utils.Binding;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.delivery.melee.Melee;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(PlayerEntity.class)
// Implements the DEPRECATED bridge type (extends SpellCaster.Player) on purpose: external
// compat mods cast players to SpellCasterEntity — the cast only works if players implement it.
public class PlayerEntityMixin implements SpellCaster.Player, SpellCasterEntity {

    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }

    private final SpellCooldownManager spellCooldownManager = new SpellCooldownManager(player());

    /// The casting authority component. The two bindings lens its synced state (process,
    /// options) onto the synced entity attachments: the server writes through them (which syncs
    /// to the player's own client and to observers), the client reads through them (the
    /// interactor parses lazily on change — no polling here).
    private final SpellCastInteractor interactor = SpellCastInteractor.forPlayer(player(),
            new Binding<>(
                    () -> SpellEngineAttachments.CAST_PROCESS.get(player()),
                    json -> {
                        if (!player().getEntityWorld().isClient()) {
                            SpellEngineAttachments.CAST_PROCESS.set(player(), json);
                        }
                    }),
            new Binding<>(
                    () -> SpellEngineAttachments.CAST_OPTIONS.get(player()),
                    json -> {
                        if (!player().getEntityWorld().isClient()) {
                            SpellEngineAttachments.CAST_OPTIONS.set(player(), json);
                        }
                    }));

    @Override
    public SpellCastInteractor getInteractor() {
        return interactor;
    }

    private ArrowShootContext arrowShotContext = ArrowShootContext.empty();
    @Override
    public void setArrowShootContext(ArrowShootContext shotContext) {
        arrowShotContext = shotContext;
    }
    @Override
    public ArrowShootContext getArrowShootContext() {
        return arrowShotContext;
    }

    @Override
    public SpellCooldownManager getCooldownManager() {
        return spellCooldownManager;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        if (player.getEntityWorld().isClient()) {
            ((AnimatablePlayer)player()).updateSpellCastAnimationsOnTick();
        } else {
            // Server side
            interactor.tick();
            if (activeAttack_serverSide != null
                    // Offsetting time by 1 tick, to compensate sync delays
                    && activeAttack_serverSide.isFinished(player.age + 1)) {
                setMeleeSkillAttack(null);
            }
        }
        spellCooldownManager.tickUpdate();
    }

    private Melee.ActiveAttack activeAttack_serverSide = null;
    @Override
    public void setMeleeSkillAttack(Melee.ActiveAttack attack) {
        activeAttack_serverSide = attack;
        float slip = 0;
        if (attack != null) {
            slip = attack.attack.movement_slip();
        }
        SpellEngineAttachments.EXTRA_SLIPPERINESS.set(player(), slip);
    }
    @Override
    public float getExtraSlipperiness() {
        return SpellEngineAttachments.EXTRA_SLIPPERINESS.get(player());
    }

    @Nullable private RegistryEntry<Spell> activeMeleeSpell = null;
    public void setActiveMeleeSkill(RegistryEntry<Spell> spell) {
        activeMeleeSpell = spell;
    }
    public RegistryEntry<Spell> getActiveMeleeSkill() {
        return activeMeleeSpell;
    }

    // MARK: Persistence

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    public void writeCustomData_TAIL_SpellEngine(WriteView view, CallbackInfo ci) {
        spellCooldownManager.writeCustomData(view);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    public void readCustomData_TAIL_SpellEngine(ReadView view, CallbackInfo ci) {
        spellCooldownManager.readCustomData(view);
    }
}
