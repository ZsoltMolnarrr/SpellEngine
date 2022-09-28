package net.combatspells.client;

import net.combatspells.CombatSpells;
import net.combatspells.entity.SpellProjectile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.combatspells.network.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.UUID;

public class ClientNetwork {
    public static void initializeHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.ID, (client, handler, buf, responseSender) -> {
            var config = Packets.ConfigSync.read(buf);
            CombatSpells.config = config;
        });

        ClientPlayNetworking.registerGlobalReceiver(SpellProjectile.SpawnPacket.ID, (client, handler, byteBuf, responseSender) -> {
            EntityType<?> et = Registry.ENTITY_TYPE.get(byteBuf.readVarInt());
            UUID uuid = byteBuf.readUuid();
            int entityId = byteBuf.readVarInt();
            Vec3d pos = SpellProjectile.SpawnPacket.PacketBufUtil.readVec3d(byteBuf);
            float pitch = SpellProjectile.SpawnPacket.PacketBufUtil.readAngle(byteBuf);
            float yaw = SpellProjectile.SpawnPacket.PacketBufUtil.readAngle(byteBuf);
            client.execute(() -> {
                if (MinecraftClient.getInstance().world == null)
                    throw new IllegalStateException("Tried to spawn entity in a null world!");
                Entity e = et.create(MinecraftClient.getInstance().world);
                if (e == null)
                    throw new IllegalStateException("Failed to create instance of entity \"" + Registry.ENTITY_TYPE.getId(et) + "\"!");
                e.updateTrackedPosition(pos.x, pos.y, pos.z);
                e.setPos(pos.x, pos.y, pos.z);
                e.setPitch(pitch);
                e.setYaw(yaw);
                e.setId(entityId);
                e.setUuid(uuid);
                MinecraftClient.getInstance().world.addEntity(entityId, e);
            });
        });
    }
}
