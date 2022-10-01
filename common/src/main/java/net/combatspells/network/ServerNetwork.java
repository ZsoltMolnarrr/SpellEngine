package net.combatspells.network;

import com.google.common.collect.Iterables;
import net.combatspells.CombatSpells;
import net.combatspells.api.SpellHelper;
import net.combatspells.internals.SpellRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class ServerNetwork {
    private static PacketByteBuf configSerialized = PacketByteBufs.create();

    public static void initializeHandlers() {
        configSerialized = Packets.ConfigSync.write(CombatSpells.config);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(Packets.ConfigSync.ID, configSerialized);
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.ReleaseRequest.ID, (server, player, handler, buf, responseSender) -> {
            ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.world)
                    .orNull();
            if (world == null || world.isClient) {
                return;
            }
            var packet = Packets.ReleaseRequest.read(buf);
            world.getServer().executeSync(() -> {
                var stack = player.getMainHandStack(); // FIXME
                var item = stack.getItem();
                var id = Registry.ITEM.getId(item);
                var spell = SpellRegistry.spells.get(id);

                List<Entity> entities = new ArrayList<>();
                for (var targetId: packet.targets()) {
                    var entity = world.getEntityById(targetId);
                    if (entity != null) {
                        entities.add(entity);
                        System.out.println("Server release on entity: " + entity.getName());
                    }
                }
                SpellHelper.castRelease(world, player, spell, 70000); // FIXME
            });
        });
    }
}
