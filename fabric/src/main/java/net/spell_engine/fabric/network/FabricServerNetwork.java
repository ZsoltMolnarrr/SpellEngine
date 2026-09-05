package net.spell_engine.fabric.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;

/// Fabric networking entrypoint (Fabric API 0.92 / 1.20.1): the serverbound play receivers and the
/// join-time sync push. There is no configuration phase on 1.20.1 — config and the spell-assignment
/// table are pushed on `ServerPlayConnectionEvents.JOIN`, exactly like the legacy 1.20.1 branch, with
/// no client acknowledgement. Decoded packets are forwarded to the loader-agnostic {@link ServerNetwork}.
/// Receivers run on the netty thread; the handlers hop to the server thread themselves (`executeSync`).
public class FabricServerNetwork {
    public static void init() {
        // Join sync: spell assignments first, then config (legacy order).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (var payload : ServerNetwork.joinSyncPayloads()) {
                sender.sendPacket(payload.id(), payload.toBuffer());
            }
        });

        // Play stage — client → server
        ServerPlayNetworking.registerGlobalReceiver(Packets.CastRequest.ID, (server, player, handler, buf, responseSender) -> {
            var packet = Packets.CastRequest.read(buf);
            ServerNetwork.handleCastRequest(packet, server, player);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.TargetStream.ID, (server, player, handler, buf, responseSender) -> {
            var packet = Packets.TargetStream.read(buf);
            ServerNetwork.handleTargetStream(packet, server, player);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.CastInput.ID, (server, player, handler, buf, responseSender) -> {
            var packet = Packets.CastInput.read(buf);
            ServerNetwork.handleCastInput(packet, server, player);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.AttackFxBroadcast.ID, (server, player, handler, buf, responseSender) -> {
            var packet = Packets.AttackFxBroadcast.read(buf);
            ServerNetwork.handleAttackFxBroadcast(packet, server, player);
        });
        ServerPlayNetworking.registerGlobalReceiver(Packets.AttackPerform.ID, (server, player, handler, buf, responseSender) -> {
            var packet = Packets.AttackPerform.read(buf);
            ServerNetwork.handleAttackPerform(packet, server, player);
        });
    }
}
