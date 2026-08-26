package net.spell_engine.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.Platform;
import net.spell_engine.PlatformClient;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.casting.ClientCastController;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.delivery.melee.Melee;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/// Attaches the local player's casting to {@link ClientCastController} (which owns the cast
/// process state machine and targeting — Phase C1 of the server-side casting rework moved the
/// logic there), and runs the client side of MELEE delivery (scheduled attacks).
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin implements SpellCaster.Client {

    private LocalPlayer player() {
        return (LocalPlayer) ((Object) this);
    }

    // MARK: Casting — delegated to the per-entity ClientCastController

    private final ClientCastController castController = new ClientCastController(player());

    @Override
    public ClientCastController getCastController() {
        return castController;
    }

    /// The one derived-read override: the LOCAL player's process is the controller's
    /// prediction (born at the input frame), not the interactor's server mirror — every
    /// interface default (current spell, casting speed, beam, ...) composes with this.
    @Override
    @Nullable public SpellCast.Process getSpellCastProcess() {
        return castController.predictedProcess();
    }

    // MARK: Melee delivery — client-side scheduled attacks

    private Melee.ActiveAttack currentAttack = null;
    private List<Melee.Attack> scheduledAttacks = new ArrayList<>();
    public void onAttacksAvailable(List<Melee.Attack> attacks) {
        scheduledAttacks.addAll(attacks);
    }
    public Melee.ActiveAttack getCurrentSkillAttack() {
        return currentAttack;
    }
    @Unique
    private void onTick_ScheduledAttacks(LocalPlayer player) {
        var time = player.tickCount;
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ATTACK)) {
            currentAttack = null;
            return;
        }
        checkForNextAttack(player, time);
        if (currentAttack != null) {
            if (currentAttack.weapon != player.getMainHandItem().getItem()) {
                // Weapon changed, cancel attack
                currentAttack = null;
                return;
            }
            if (currentAttack.isDue(time)) {
                onAttackHit(currentAttack);
            }
            if (currentAttack.isFinished(time)) {
                currentAttack = null;
                checkForNextAttack(player, time);
            }
        }
    }
    private void checkForNextAttack(LocalPlayer player, int time) {
        if (currentAttack == null) {
            if (!scheduledAttacks.isEmpty()) {
                var attack = scheduledAttacks.remove(0);
                currentAttack = new Melee.ActiveAttack(attack, time, player.getMainHandItem().getItem());
                onAttackActivated(attack);
            }
        }
    }

    @Unique
    private void onAttackActivated(Melee.Attack attack) {
        // On attack started

        var player = player();
        var momentum = attack.forward_momentum();
        if (momentum > 0
                && (attack.allow_momentum_airborne() || player.onGround()) ) {
            var direction = new Vec3(0, 0, 1)
                    .yRot((float) Math.toRadians((-1.0) * player.getYRot()))
                    .scale(attack.forward_momentum());
            player.push(direction.x, direction.y, direction.z);
        }

        if (attack.context() != null) {
            var animationSpeed = attack.speed() * attack.animation().speed;
            ((AnimatablePlayer)this).playSpellAnimation(SpellCast.Animation.RELEASE, attack.animation().id, animationSpeed);
            var packet = new Packets.AttackFxBroadcast(attack.context());
            Platform.util().networkC2S_Send(packet);
        }
    }
    @Unique
    private void onAttackHit(Melee.ActiveAttack activeAttack) {
        var player = player();
        var attack = activeAttack.attack;
        var targets = Melee.detectTargets(player, attack);
        if (!attack.additional_hits_on_same_target()) {
            targets = targets.stream().filter(id -> !activeAttack.hitEntityIds.contains(id)).toList();
        }
        activeAttack.hitEntityIds.addAll(targets);
        if (!targets.isEmpty()) {
            var targetIds = targets.stream().mapToInt(Integer::intValue).toArray();
            var context = attack.context() != null ? attack.context() : Melee.AttackContext.EMPTY;
            var packet = new Packets.AttackPerform(context, targetIds);
            Platform.util().networkC2S_Send(packet);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        castController.tick();
        if (isBeaming()) {
            PlatformClient.util().sendVanillaPacket_C2S(player, new ServerboundMovePlayerPacket.PosRot(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.onGround(), player.horizontalCollision)
            );
        }
        onTick_ScheduledAttacks(player);
    }
}
