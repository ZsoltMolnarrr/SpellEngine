package net.spell_engine.utils;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.network.Packets;

import java.util.Collection;

public class AnimationHelper {
    public static void sendAnimation(PlayerEntity animatedPlayer, Collection<ServerPlayerEntity> trackingPlayers, SpellCast.Animation type, String name, float speed) {
        if (name == null || name.isEmpty()) {
            return;
        }
        var packet = new Packets.SpellAnimation(animatedPlayer.getId(), type, name, speed).write();
        if (animatedPlayer instanceof ServerPlayerEntity serverPlayer) {
            sendPacketToPlayer(serverPlayer, packet);
        }
        trackingPlayers.forEach(serverPlayer -> {
            sendPacketToPlayer(serverPlayer, packet);
        });
    }

    private static void sendPacketToPlayer(ServerPlayerEntity serverPlayer, PacketByteBuf packet) {
        try {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.SpellAnimation.ID)) {
                ServerPlayNetworking.send(serverPlayer, Packets.SpellAnimation.ID, packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
