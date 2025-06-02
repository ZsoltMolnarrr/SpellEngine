package net.spell_engine.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.fx.ParticleHelper;

public class ClientNetwork {
    public static void initializeHandlers() {
        ClientConfigurationNetworking.registerGlobalReceiver(Packets.ConfigSync.PACKET_ID, (packet, context) -> {
            SpellEngineMod.config = packet.config();
            context.responseSender().sendPacket(new Packets.Ack(ServerNetwork.ConfigurationTask.name));
        });

        ClientConfigurationNetworking.registerGlobalReceiver(Packets.SpellRegistrySync.PACKET_ID, (packet, context) -> {
            SpellAssignments.decodeContent(packet.chunks());
            context.responseSender().sendPacket(new Packets.Ack(ServerNetwork.SpellRegistrySyncTask.name));
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.ParticleBatches.PACKET_ID, (packet, context) -> {
            var client = context.client();
            var instructions = ParticleHelper.convertToInstructions(client.world, packet);
            client.execute(() -> {
                for(var instruction: instructions) {
                    instruction.perform(client.world);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellAnimation.PACKET_ID, (packet, context) -> {
            var client = context.client();
            client.execute(() -> {
                var entity = client.world.getEntityById(packet.playerId());
                if (entity instanceof PlayerEntity player) {
                    ((AnimatablePlayer)player).playSpellAnimation(packet.type(), packet.name(), packet.speed());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldown.PACKET_ID, (packet, context) -> {
            var client = context.client();
            client.execute(() -> {
                ((SpellCasterEntity)client.player).getCooldownManager().set(packet.spellId(), packet.duration());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldownSync.PACKET_ID, (packet, context) -> {
            var client = context.client();
            client.execute(() -> {
                ((SpellCasterEntity)client.player).getCooldownManager().acceptSync(packet.baseTick(), packet.cooldowns());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellContainerSync.PACKET_ID, (packet, context) -> {
            var client = context.client();
            client.execute(() -> {
                var player = client.player;
                if (player != null) {
                    var containers = ((SpellContainerSource.Owner) player).serverSideSpellContainers();
                    containers.clear();
                    containers.putAll(packet.containers());
                }
                SpellContainerSource.setDirty(client.player, SpellContainerSource.MAIN_HAND);
            });
        });
    }
}
