package net.spell_engine.internals;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.casting.SpellCasterEntity;

import java.util.Collection;

public class SpellCastSyncHelper {
    public static void setCasting(PlayerEntity caster, Hand hand, Identifier spellId, Collection<ServerPlayerEntity> trackingPlayers) {
        ((SpellCasterEntity)caster).setCurrentSpellId(spellId);
        caster.setCurrentHand(hand);
    }

    public static void clearCasting(PlayerEntity caster) {
        clearCasting(caster, PlayerLookup.tracking(caster));
    }

    public static void clearCasting(PlayerEntity caster, Collection<ServerPlayerEntity> trackingPlayers) {
        ((SpellCasterEntity)caster).setCurrentSpellId(null);
    }
}
