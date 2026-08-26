package net.spell_engine.misc;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SpellEngineCommands {
    public static void register() {
        PlatformEvents.onCommandRegistration((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("spell_cooldown")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("reset").then(
                            Commands.argument("players", EntityArgument.player())
                                    .then(Commands.argument("spell", ResourceArgument.resource(registryAccess, SpellRegistry.KEY))
                                            .executes(context -> {
                                                var players = EntityArgument.getPlayers(context, "players");
                                                var spell = ResourceArgument.getResource(context, "spell", SpellRegistry.KEY);
                                                return executeResetCooldown(players, spell);
                                            })
                                    )
                    ))
                    .then(Commands.literal("clear").then(
                            Commands.argument("players", EntityArgument.players())
                                    .executes(context -> {
                                        var players = EntityArgument.getPlayers(context, "players");
                                        return executeResetCooldown(players, null);
                                    })
                    ))
            );
        });
    }

    private static int executeResetCooldown(Collection<ServerPlayer> players, @Nullable Holder<Spell> spell) {
        for (var player: players) {
            Identifier spellId = null;
            if (spell != null) {
                spellId = spell.unwrapKey().get().identifier();
            }
            ((SpellCaster.Player) player).getCooldownManager().reset(spellId);
        }
        return 0;
    }
}
