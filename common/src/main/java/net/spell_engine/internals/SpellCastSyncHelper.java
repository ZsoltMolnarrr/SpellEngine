package net.spell_engine.internals;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.network.Packets;

import java.util.Collection;

public class SpellCastSyncHelper {
    public static void setCasting(PlayerEntity caster, Identifier spellId, Collection<ServerPlayerEntity> trackingPlayers) {
        ((SpellCasterEntity)caster).setCurrentSpell(spellId);
        var packet = new Packets.SpellCastSync(caster.getId(), spellId).write();
        trackingPlayers.forEach(serverPlayer -> {
            ServerPlayNetworking.send(serverPlayer, Packets.SpellCastSync.ID, packet);
        });
    }

    public static void clearCasting(PlayerEntity caster) {
        clearCasting(caster, PlayerLookup.tracking(caster));
    }

    public static void clearCasting(PlayerEntity caster, Collection<ServerPlayerEntity> trackingPlayers) {
        ((SpellCasterEntity)caster).setCurrentSpell(null);
        var packet = Packets.SpellCastSync.clear(caster.getId()).write();
        trackingPlayers.forEach(serverPlayer -> {
            ServerPlayNetworking.send(serverPlayer, Packets.SpellCastSync.ID, packet);
        });
    }
}
