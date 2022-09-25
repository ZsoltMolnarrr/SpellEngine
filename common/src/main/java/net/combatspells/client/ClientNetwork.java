package net.combatspells.client;

import net.combatspells.CombatSpells;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.combatspells.network.Packets;

public class ClientNetwork {
    public static void initializeHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.ID, (client, handler, buf, responseSender) -> {
            var config = Packets.ConfigSync.read(buf);
            CombatSpells.config = config;
        });
    }
}
