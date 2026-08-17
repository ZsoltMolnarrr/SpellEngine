package net.spell_engine.mixin.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.Platform;
import net.spell_engine.PlatformClient;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.casting.ClientCastController;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.melee.Melee;
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
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCaster.Client {

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
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
    private void onTick_ScheduledAttacks(ClientPlayerEntity player) {
        var time = player.age;
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ATTACK)) {
            currentAttack = null;
            return;
        }
        checkForNextAttack(player, time);
        if (currentAttack != null) {
            if (currentAttack.weapon != player.getMainHandStack().getItem()) {
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
    private void checkForNextAttack(ClientPlayerEntity player, int time) {
        if (currentAttack == null) {
            if (!scheduledAttacks.isEmpty()) {
                var attack = scheduledAttacks.remove(0);
                currentAttack = new Melee.ActiveAttack(attack, time, player.getMainHandStack().getItem());
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
                && (attack.allow_momentum_airborne() || player.isOnGround()) ) {
            var direction = new Vec3d(0, 0, 1)
                    .rotateY((float) Math.toRadians((-1.0) * player.getYaw()))
                    .multiply(attack.forward_momentum());
            player.addVelocity(direction.x, direction.y, direction.z);
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
            PlatformClient.util().sendVanillaPacket_C2S(player, new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    player.isOnGround())
            );
        }
        onTick_ScheduledAttacks(player);
    }
}
