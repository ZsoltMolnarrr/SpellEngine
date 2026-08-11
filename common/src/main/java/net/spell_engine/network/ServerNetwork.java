package net.spell_engine.network;

import com.google.common.collect.Iterables;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.casting.SpellCastSyncHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.melee.Melee;
import net.spell_engine.internals.target.SpellTarget;

import java.util.ArrayList;
import java.util.List;

/// Server-side packet handling. This class is loader-agnostic: it holds only the handler
/// bodies. Payload registration, configuration tasks and the lifecycle event wiring live in
/// each loader's own network entrypoint (`FabricServerNetwork` / NeoForge `NetworkEvents`),
/// which forwards decoded packets here.
public class ServerNetwork {
    // Configuration-task identifiers. Shared so the client-side Ack and both loaders' task
    // implementations agree on the same names.
    public static final String CONFIG_TASK_NAME = SpellEngineMod.ID + ":" + "config";
    public static final String SPELL_REGISTRY_TASK_NAME = SpellEngineMod.ID + ":" + "spell_registry";

    public static void handleSpellCastSync(Packets.SpellCastSync packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }

        world.getServer().executeSync(() -> {
            if (packet.spellId() == null) {
                SpellCastSyncHelper.clearCasting(player);
            } else {
                SpellHelper.startCasting(player, packet.spellId(), packet.speed(), packet.length());
            }
        });
    }

    public static void handleSpellRequest(Packets.SpellRequest packet, MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = Iterables.tryFind(server.getWorlds(), (element) -> element == player.getWorld())
                .orNull();
        if (world == null || world.isClient) {
            return;
        }

        world.getServer().executeSync(() -> {
            var spellEntry = SpellRegistry.from(world).getEntry(packet.spellId());
            if (spellEntry.isEmpty()) {
                return;
            }
            List<Entity> targets = new ArrayList<>();
            for (var targetId: packet.targets()) {
                // var entity = world.getEntityById(targetId);
                var entity = world.getDragonPart(targetId); // Retrieves `getEntityById` + dragon parts :)
                if (entity != null) {
                    targets.add(entity);
                } else {
                    System.err.println("Spell Engine: Trying to perform spell " + packet.spellId().toString() + " Entity not found: " + targetId);
                }
            }
            var target = new SpellTarget.SearchResult(targets, packet.location());
            SpellHelper.performSpell(world, player, spellEntry.get(), target, packet.action(), packet.progress());
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
