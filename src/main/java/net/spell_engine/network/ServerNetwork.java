package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.List;

public class ServerNetwork {
    private static PacketByteBuf configSerialized = PacketByteBufs.create();

    public static void initializeHandlers() {
        configSerialized = Packets.ConfigSync.write(SpellEngineMod.config);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(Packets.SpellRegistrySync.ID, SpellRegistry.encoded);
            sender.sendPacket(Packets.ConfigSync.ID, configSerialized);
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.SpellRequest.ID, (server, player, handler, buf, responseSender) -> {
            ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                    .orNull();
            if (world == null || world.isClient) {
                return;
            }
            var packet = Packets.SpellRequest.read(buf);
            world.getServer().executeSync(() -> {
                var stack = player.getInventory().getStack(packet.slot());
                List<Entity> targets = new ArrayList<>();
                for (var targetId: packet.targets()) {
                    var entity = world.getEntityById(targetId);
                    if (entity != null) {
                        targets.add(entity);
                    }
                }
                SpellHelper.performSpell(world, player, packet.spellId(), targets, stack, packet.action(), packet.hand(), packet.progress());
            });
        });
    }
}
