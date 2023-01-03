package net.spell_engine.client;

import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.internals.SpellCasterEntity;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.ParticleHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;

public class ClientNetwork {
    public static void initializeHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.ID, (client, handler, buf, responseSender) -> {
            var config = Packets.ConfigSync.read(buf);
            SpellEngineMod.config = config;
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellRegistrySync.ID, (client, handler, buf, responseSender) -> {
            SpellRegistry.decodeContent(buf);
            CustomModelRegistry.load();
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.ParticleBatches.ID, (client, handler, buf, responseSender) -> {
            var packet = Packets.ParticleBatches.read(buf);
            var instructions = ParticleHelper.convertToInstructions(client.world, 0, 0, packet);
            client.execute(() -> {
                for(var instruction: instructions) {
                    instruction.perform(client.world);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCastSync.ID, (client, handler, buf, responseSender) -> {
            var packet = Packets.SpellCastSync.read(buf);
            client.execute(() -> {
                var entity = client.world.getEntityById(packet.playerId());
                if (entity instanceof SpellCasterEntity caster) {
                    if (packet.spellId().equals(Packets.SpellCastSync.CLEAR_SYMBOL)) {
                        caster.setCurrentSpell(null);
                    } else {
                        caster.setCurrentSpell(packet.spellId());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellAnimation.ID, (client, handler, buf, responseSender) -> {
            var packet = Packets.SpellAnimation.read(buf);
            client.execute(() -> {
                var entity = client.world.getEntityById(packet.playerId());
                if (entity instanceof PlayerEntity player) {
                    ((AnimatablePlayer)player).playSpellAnimation(packet.type(), packet.name());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldown.ID, (client, handler, buf, responseSender) -> {
            var packet = Packets.SpellCooldown.read(buf);
            client.execute(() -> {
                ((SpellCasterEntity)client.player).getCooldownManager().set(packet.spellId(), packet.duration());
            });
        });
    }
}
