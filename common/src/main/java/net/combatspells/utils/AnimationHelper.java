package net.combatspells.utils;

import net.combatspells.internals.SpellAnimationType;
import net.combatspells.network.Packets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

public class AnimationHelper {
    public static void sendAnimation(PlayerEntity animatedPlayer, SpellAnimationType type, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        var packet = new Packets.SpellAnimation(animatedPlayer.getId(), type, name).write();
        if (animatedPlayer instanceof ServerPlayerEntity serverPlayer) {
            sendPacketToPlayer(serverPlayer, packet);
        }
        PlayerLookup.tracking(animatedPlayer).forEach(serverPlayer -> {
            sendPacketToPlayer(serverPlayer, packet);
        });
    }

    private static void sendPacketToPlayer(ServerPlayerEntity serverPlayer, PacketByteBuf packet) {
        try {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.SpellAnimation.ID)) {
                ServerPlayNetworking.send(serverPlayer, Packets.SpellAnimation.ID, packet);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
