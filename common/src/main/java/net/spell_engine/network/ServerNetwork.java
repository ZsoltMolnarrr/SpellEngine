package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.delivery.melee.Melee;

import java.util.List;


/// Server-side packet handling. This class is loader-agnostic: it holds only the handler
/// bodies. Payload registration and the lifecycle event wiring live in each loader's own
/// network entrypoint (`FabricServerNetwork` / Forge `ForgeNetwork`), which forwards decoded
/// packets here.
public class ServerNetwork {
    // MARK: Join sync (1.20.1 has no configuration phase)

    /// The payloads pushed to a player right after they join, in send order: the spell
    /// assignment table first, then the server config — exactly the legacy 1.20.1 handshake.
    /// No client "ready" acknowledgement exists; the client handlers write straight into statics.
    /// Config is serialized per join (cheap) instead of once at init.
    public static List<Packets.Payload> joinSyncPayloads() {
        return List.of(
                new Packets.SpellRegistrySync(SpellAssignments.encoded),
                new Packets.ConfigSync(SpellEngineMod.config)
        );
    }

    // MARK: Casting protocol — signals into the caster's SpellCastInteractor

    public static void handleCastRequest(Packets.CastRequest packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCaster.Player) player).getInteractor().requestCast(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleTargetStream(Packets.TargetStream packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCaster.Player) player).getInteractor().submitTargets(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleCastInput(Packets.CastInput packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCaster.Player) player).getInteractor().requestEnd(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleAttackFxBroadcast(Packets.AttackFxBroadcast packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }

        world.getServer().executeSync(() -> {
            Melee.broadcastAttackFx(player, packet.attackContext());
        });
    }

    public static void handleAttackPerform(Packets.AttackPerform packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }

        world.getServer().executeSync(() -> {
            Melee.performAttackAgainstTargets(player, packet.attackContext(), packet.targetIds());
        });
    }

    /// Invoked when a player joins or changes dimension: re-sync their spell cooldowns and
    /// server-side spell containers. Wired to `ServerPlayConnectionEvents.JOIN` /
    /// `ServerEntityWorldChangeEvents` on Fabric and to `PlayerEvent` on NeoForge.
    public static void onPlayerConnectOrChangeWorld(ServerPlayerEntity player) {
        ((SpellCaster.Player) player).getCooldownManager().pushSync();
        SpellContainerSource.syncServerSideContainers(player);
    }
}
