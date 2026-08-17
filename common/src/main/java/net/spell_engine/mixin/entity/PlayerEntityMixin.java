package net.spell_engine.mixin.entity;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
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


@Mixin(value = PlayerEntity.class, priority = 555)
// Implements the DEPRECATED bridge type (extends SpellCaster.Player) on purpose: external
// compat mods cast players to SpellCasterEntity — the cast only works if players implement it.
public class PlayerEntityMixin implements SpellCaster.Player, SpellCasterEntity {

    private PlayerEntity player() {
        return (PlayerEntity) ((Object) this);
    }

    private final SpellCooldownManager spellCooldownManager = new SpellCooldownManager(player());

    /// The casting authority component. The two bindings lens its synced state (process,
    /// options) onto the tracked data slots below: the server writes through them, the client
    /// reads through them (the interactor parses lazily on change — no polling here).
    private final SpellCastInteractor interactor = SpellCastInteractor.forPlayer(player(),
            new Binding<>(
                    () -> player().getDataTracker().get(SPELL_ENGINE_SPELL_PROGRESS),
                    json -> {
                        if (!player().getWorld().isClient) {
                            player().getDataTracker().set(SPELL_ENGINE_SPELL_PROGRESS, json);
                        }
                    }),
            new Binding<>(
                    () -> player().getDataTracker().get(SPELL_ENGINE_OPTIONS),
                    json -> {
                        if (!player().getWorld().isClient) {
                            player().getDataTracker().set(SPELL_ENGINE_OPTIONS, json);
                        }
                    }));

    @Override
    public SpellCastInteractor getInteractor() {
        return interactor;
    }

    private static final TrackedData<String> SPELL_ENGINE_SPELL_PROGRESS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> SPELL_ENGINE_OPTIONS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Float> SPELL_ENGINE_EXTRA_SLIPPERINESS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SPELL_ENGINE_SPELL_PROGRESS, "");
        builder.add(SPELL_ENGINE_OPTIONS, "");
        builder.add(SPELL_ENGINE_EXTRA_SLIPPERINESS, 0F);
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
        if (player.getWorld().isClient) {
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
        player().getDataTracker().set(SPELL_ENGINE_EXTRA_SLIPPERINESS, slip);
    }
    @Override
    public float getExtraSlipperiness() {
        return player().getDataTracker().get(SPELL_ENGINE_EXTRA_SLIPPERINESS);
    }

    @Nullable private RegistryEntry<Spell> activeMeleeSpell = null;
    public void setActiveMeleeSkill(RegistryEntry<Spell> spell) {
        activeMeleeSpell = spell;
    }
    public RegistryEntry<Spell> getActiveMeleeSkill() {
        return activeMeleeSpell;
    }

    // MARK: Persistence

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void writeCustomDataToNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        spellCooldownManager.writeCustomDataToNbt(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void readCustomDataFromNbt_TAIL_SpellEngine(NbtCompound nbt, CallbackInfo ci) {
        spellCooldownManager.readCustomDataFromNbt(nbt);
    }
}
