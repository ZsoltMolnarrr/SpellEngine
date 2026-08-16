package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.melee.Melee;


/// Server-side packet handling. This class is loader-agnostic: it holds only the handler
/// bodies. Payload registration, configuration tasks and the lifecycle event wiring live in
/// each loader's own network entrypoint (`FabricServerNetwork` / NeoForge `NetworkEvents`),
/// which forwards decoded packets here.
public class ServerNetwork {
    // Configuration-task identifiers. Shared so the client-side Ack and both loaders' task
    // implementations agree on the same names.
    public static final String CONFIG_TASK_NAME = SpellEngineMod.ID + ":" + "config";
    public static final String SPELL_REGISTRY_TASK_NAME = SpellEngineMod.ID + ":" + "spell_registry";

    // MARK: Casting protocol — signals into the caster's SpellCastInteractor

    public static void handleCastRequest(Packets.CastRequest packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCasterEntity) player).getInteractor().requestCast(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleTargetStream(Packets.TargetStream packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCasterEntity) player).getInteractor().submitTargets(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleCastInput(Packets.CastInput packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }
        world.getServer().executeSync(() -> {
            ((SpellCasterEntity) player).getInteractor().requestEnd(packet.spellId(), packet.snapshot());
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
        ((SpellCasterEntity) player).getCooldownManager().pushSync();
        SpellContainerSource.syncServerSideContainers(player);
    }
}
