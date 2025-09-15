package net.spell_engine.internals;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SpellEngineCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spell_cooldown")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("reset").then(
                            CommandManager.argument("players", EntityArgumentType.player())
                                    .then(CommandManager.argument("spell", RegistryEntryReferenceArgumentType.registryEntry(registryAccess, SpellRegistry.KEY))
                                            .executes(context -> {
                                                var players = EntityArgumentType.getPlayers(context, "players");
                                                var spell = RegistryEntryReferenceArgumentType.getRegistryEntry(context, "spell", SpellRegistry.KEY);
                                                return executeResetCooldown(players, spell);
                                            })
                                    )
                    ))
                    .then(CommandManager.literal("clear").then(
                            CommandManager.argument("players", EntityArgumentType.players())
                                    .executes(context -> {
                                        var players = EntityArgumentType.getPlayers(context, "players");
                                        return executeResetCooldown(players, null);
                                    })
                    ))
            );
        });
    }

    private static int executeResetCooldown(Collection<ServerPlayerEntity> players, @Nullable RegistryEntry<Spell> spell) {
        for (var player: players) {
            Identifier spellId = null;
            if (spell != null) {
                spellId = spell.getKey().get().getValue();
            }
            ((SpellCasterEntity) player).getCooldownManager().reset(spellId);
        }
        return 0;
    }
}
