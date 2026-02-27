package net.spell_engine.utils;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.network.Packets;

import java.util.Collection;

public class AnimationHelper {
    public static void sendAnimationExcluding(PlayerEntity animatedPlayer, Collection<ServerPlayerEntity> trackingPlayers, SpellCast.Animation type, PlayerAnimation animation, float speed) {
        sendAnimation(animatedPlayer, false, trackingPlayers, type, animation, speed);
    }
    public static void sendAnimation(PlayerEntity animatedPlayer, Collection<ServerPlayerEntity> trackingPlayers, SpellCast.Animation type, PlayerAnimation animation, float speed) {
        sendAnimation(animatedPlayer, true, trackingPlayers, type, animation, speed);
    }
    public static void sendAnimation(PlayerEntity animatedPlayer, boolean includeAnimated, Collection<ServerPlayerEntity> trackingPlayers, SpellCast.Animation type, PlayerAnimation animation, float speed) {
        var id = getAnimationId(animatedPlayer, animation);
        if (id == null || id.isEmpty()) {
            return;
        }
        var packet = new Packets.SpellAnimation(animatedPlayer.getId(), type, id, speed * animation.speed);
        if (includeAnimated && animatedPlayer instanceof ServerPlayerEntity serverPlayer) {
            sendPacketToPlayer(serverPlayer, packet);
        }
        trackingPlayers.forEach(serverPlayer -> {
            sendPacketToPlayer(serverPlayer, packet);
        });
    }

    private static void sendPacketToPlayer(ServerPlayerEntity serverPlayer, Packets.SpellAnimation packet) {
        try {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.SpellAnimation.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getAnimationId(LivingEntity entity, PlayerAnimation animation) {
        if (animation == null) {
            return null;
        }
        return animation.resolve(entity);
    }
}
