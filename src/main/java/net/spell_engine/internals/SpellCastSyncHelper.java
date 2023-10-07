package net.spell_engine.internals;

import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;

public class SpellCastSyncHelper {
    public static void setCasting(PlayerEntity caster, SpellCast.Process process) {
        //System.out.println("Setting casting process to " + process);
        ((SpellCasterEntity)caster).setSpellCastProcess(process);
    }

    public static void clearCasting(PlayerEntity caster) {
        //System.out.println("Clearing casting process");
        ((SpellCasterEntity)caster).setSpellCastProcess(null);
    }
}
