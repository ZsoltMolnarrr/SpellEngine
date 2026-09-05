package net.spell_engine.forge.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// Forge 47 networking entrypoint: one `SimpleChannel` carrying every Spell Engine payload
/// (`Packets.*` records, encoded with their `write`/`read` pairs), plus the join-time sync push.
/// No configuration phase exists on 1.20.1 — config and the spell-assignment table are pushed on
/// `PlayerLoggedInEvent`, mirroring the Fabric `ServerPlayConnectionEvents.JOIN` push and the legacy
/// 1.20.1 handshake (no client acknowledgement). Serverbound packets are forwarded to the
/// loader-agnostic {@link ServerNetwork}; clientbound ones to {@link ForgeClientNetwork}, which is
/// only resolved (lambda bodies, not method refs) when a clientbound message is actually handled —
/// never on a dedicated server.
///
/// Wiring: the Forge entrypoint must call {@link #register()} once from its constructor (any time
/// during mod construction; message ids are assigned in registration order, so keep it deterministic).
public class ForgeNetwork {
    public static final Identifier CHANNEL_NAME = new Identifier(SpellEngineMod.ID, "main");
    private static final String PROTOCOL_VERSION = "1";

    /// Lenient version predicates: a peer without the channel (vanilla client, SE-less server) is
    /// accepted, like Fabric's behaviour; `isRemotePresent` backs `networkS2C_CanSend` for callers
    /// that care.
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(CHANNEL_NAME)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION))
            .simpleChannel();

    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;

        // Play stage — client → server
        id = c2s(id, Packets.CastRequest.class, Packets.CastRequest::write, Packets.CastRequest::read,
                (packet, player) -> ServerNetwork.handleCastRequest(packet, player.server, player));
        id = c2s(id, Packets.TargetStream.class, Packets.TargetStream::write, Packets.TargetStream::read,
                (packet, player) -> ServerNetwork.handleTargetStream(packet, player.server, player));
        id = c2s(id, Packets.CastInput.class, Packets.CastInput::write, Packets.CastInput::read,
                (packet, player) -> ServerNetwork.handleCastInput(packet, player.server, player));
        id = c2s(id, Packets.AttackPerform.class, Packets.AttackPerform::write, Packets.AttackPerform::read,
                (packet, player) -> ServerNetwork.handleAttackPerform(packet, player.server, player));
        id = c2s(id, Packets.AttackFxBroadcast.class, Packets.AttackFxBroadcast::write, Packets.AttackFxBroadcast::read,
                (packet, player) -> ServerNetwork.handleAttackFxBroadcast(packet, player.server, player));

        // Join sync + play stage — server → client
        id = s2c(id, Packets.ConfigSync.class, Packets.ConfigSync::write, Packets.ConfigSync::read,
                packet -> ForgeClientNetwork.handleConfigSync(packet));
        id = s2c(id, Packets.SpellRegistrySync.class, Packets.SpellRegistrySync::write, Packets.SpellRegistrySync::read,
                packet -> ForgeClientNetwork.handleSpellRegistrySync(packet));
        id = s2c(id, Packets.SpellCooldown.class, Packets.SpellCooldown::write, Packets.SpellCooldown::read,
                packet -> ForgeClientNetwork.handleSpellCooldown(packet));
        id = s2c(id, Packets.SpellCooldownSync.class, Packets.SpellCooldownSync::write, Packets.SpellCooldownSync::read,
                packet -> ForgeClientNetwork.handleSpellCooldownSync(packet));
        id = s2c(id, Packets.SpellMessage.class, Packets.SpellMessage::write, Packets.SpellMessage::read,
                packet -> ForgeClientNetwork.handleSpellMessage(packet));
        id = s2c(id, Packets.ParticleEffects.class, Packets.ParticleEffects::write, Packets.ParticleEffects::read,
                packet -> ForgeClientNetwork.handleParticleEffects(packet));
        id = s2c(id, Packets.SpellAnimation.class, Packets.SpellAnimation::write, Packets.SpellAnimation::read,
                packet -> ForgeClientNetwork.handleSpellAnimation(packet));
        id = s2c(id, Packets.SpellContainerSync.class, Packets.SpellContainerSync::write, Packets.SpellContainerSync::read,
                packet -> ForgeClientNetwork.handleSpellContainerSync(packet));
        id = s2c(id, Packets.AttackAvailable.class, Packets.AttackAvailable::write, Packets.AttackAvailable::read,
                packet -> ForgeClientNetwork.handleAttackAvailable(packet));

        // Join sync push (Forge game bus). Explicit event class: the 4-arg overload avoids the
        // lambda-type inference of the plain addListener(Consumer).
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, ForgeNetwork::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) {
            // Spell assignments first, then config (legacy order).
            for (var payload : ServerNetwork.joinSyncPayloads()) {
                sendToPlayer(player, payload);
            }
        }
    }

    // MARK: Send helpers (used by the Forge Platform.Util impl)

    public static void sendToPlayer(ServerPlayerEntity player, Packets.Payload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Packets.Payload payload) {
        CHANNEL.sendToServer(payload);
    }

    /// Whether the player's client negotiated this channel (false for vanilla / SE-less clients).
    public static boolean canSendToPlayer(ServerPlayerEntity player) {
        return player.networkHandler != null && CHANNEL.isRemotePresent(player.networkHandler.connection);
    }

    // MARK: Registration helpers

    private static <T> int c2s(int id, Class<T> type, BiConsumer<T, PacketByteBuf> encoder, Function<PacketByteBuf, T> decoder,
                               BiConsumer<T, ServerPlayerEntity> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((packet, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    var player = context.getSender();
                    if (player != null) {
                        handler.accept(packet, player);
                    }
                })
                .add();
        return id + 1;
    }

    private static <T> int s2c(int id, Class<T> type, BiConsumer<T, PacketByteBuf> encoder, Function<PacketByteBuf, T> decoder,
                               Consumer<T> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((packet, contextSupplier) -> handler.accept(packet))
                .add();
        return id + 1;
    }
}
