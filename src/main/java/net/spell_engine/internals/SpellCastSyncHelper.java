package net.spell_engine.internals;

import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;

public class SpellCastSyncHelper {
    public static void setCasting(PlayerEntity caster, SpellCast.Process process) {
        ((SpellCasterEntity)caster).setSpellCastProcess(process);
    }

    public static void clearCasting(PlayerEntity caster) {
        ((SpellCasterEntity)caster).setSpellCastProcess(null);
    }
}
