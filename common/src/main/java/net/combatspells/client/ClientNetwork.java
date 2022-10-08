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
            var source = client.world.getEntityById(packet.sourceEntityId());
            var instructions = ParticleHelper.convertToInstructions(source,0, 0, packet.batches());
            client.execute(() -> {
                for(var instruction: instructions) {
                    instruction.perform(client.world);
                }
            });
        });
    }
}