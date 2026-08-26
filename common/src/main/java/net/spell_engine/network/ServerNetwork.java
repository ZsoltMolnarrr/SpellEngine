package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.delivery.melee.Melee;


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

    public static void handleCastRequest(Packets.CastRequest packet, MinecraftServer server, ServerPlayer player) {
        ServerLevel world = Iterables.tryFind(server.getAllLevels(), (element) -> element == player.level())
                .orNull();
        if (world == null || world.isClientSide()) {
            return;
        }
        world.getServer().executeIfPossible(() -> {
            ((SpellCaster.Player) player).getInteractor().requestCast(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleTargetStream(Packets.TargetStream packet, MinecraftServer server, ServerPlayer player) {
        ServerLevel world = Iterables.tryFind(server.getAllLevels(), (element) -> element == player.level())
                .orNull();
        if (world == null || world.isClientSide()) {
            return;
        }
        world.getServer().executeIfPossible(() -> {
            ((SpellCaster.Player) player).getInteractor().submitTargets(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleCastInput(Packets.CastInput packet, MinecraftServer server, ServerPlayer player) {
        ServerLevel world = Iterables.tryFind(server.getAllLevels(), (element) -> element == player.level())
                .orNull();
        if (world == null || world.isClientSide()) {
            return;
        }
        world.getServer().executeIfPossible(() -> {
            ((SpellCaster.Player) player).getInteractor().requestEnd(packet.spellId(), packet.snapshot());
        });
    }

    public static void handleAttackFxBroadcast(Packets.AttackFxBroadcast packet, MinecraftServer server, ServerPlayer player) {
        ServerLevel world = Iterables.tryFind(server.getAllLevels(), (element) -> element == player.level())
                .orNull();
        if (world == null || world.isClientSide()) {
            return;
        }

        world.getServer().executeIfPossible(() -> {
            Melee.broadcastAttackFx(player, packet.attackContext());
        });
    }

    public static void handleAttackPerform(Packets.AttackPerform packet, MinecraftServer server, ServerPlayer player) {
        ServerLevel world = Iterables.tryFind(server.getAllLevels(), (element) -> element == player.level())
                .orNull();
        if (world == null || world.isClientSide()) {
            return;
        }

        world.getServer().executeIfPossible(() -> {
            Melee.performAttackAgainstTargets(player, packet.attackContext(), packet.targetIds());
        });
    }

    /// Invoked when a player joins or changes dimension: re-sync their spell cooldowns and
    /// server-side spell containers. Wired to `ServerPlayConnectionEvents.JOIN` /
    /// `ServerEntityWorldChangeEvents` on Fabric and to `PlayerEvent` on NeoForge.
    public static void onPlayerConnectOrChangeWorld(ServerPlayer player) {
        ((SpellCaster.Player) player).getCooldownManager().pushSync();
        SpellContainerSource.syncServerSideContainers(player);
    }
}
