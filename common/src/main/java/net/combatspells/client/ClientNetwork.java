package net.combatspells.client;

import net.combatspells.CombatSpells;
import net.combatspells.network.Packets;
import net.combatspells.utils.ParticleHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetwork {
    public static void initializeHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.ID, (client, handler, buf, responseSender) -> {
            var config = Packets.ConfigSync.read(buf);
            CombatSpells.config = config;
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.ParticleBatches.ID, (client, handler, buf, responseSender) -> {
            var packet = Packets.ParticleBatches.read(buf);
            client.execute(() -> {
                var source = client.world.getEntityById(packet.sourceEntityId());
                for(var batch: packet.batches()) {
                    ParticleHelper.play(client.world, source, batch);
                }
            });
        });
    }
}
