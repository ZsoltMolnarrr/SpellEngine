package net.spell_engine.internals.casting;

import net.minecraft.entity.player.PlayerEntity;

public class SpellCastSyncHelper {
    public static void setCasting(PlayerEntity caster, SpellCast.Process process) {
        //System.out.println("Setting casting process to " + process);
        ((SpellCasterEntity)caster).setSpellCastProcess(process);
    }

    public static void clearCasting(PlayerEntity caster) {
        //System.out.println("Clearing casting process");
        ((SpellCasterEntity)caster).setSpellCastProcess(null);
        ((SpellCasterEntity)caster).setChannelTickIndex(0);
    }
}
