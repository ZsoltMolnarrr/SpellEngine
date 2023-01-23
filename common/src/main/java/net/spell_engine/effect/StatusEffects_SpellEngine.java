package net.spell_engine.effect;

import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_power.api.MagicSchool;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class StatusEffects_SpellEngine {

    public static Map<Identifier, SilenceEffect> SILENCES;
    public static SilenceEffect ALLSILENCE = new SilenceEffect(StatusEffectCategory.HARMFUL,10395294,null);
    public static SilenceEffect getSilence(@Nullable MagicSchool school){
        if(school == null){
            return ALLSILENCE;
        }
        return SILENCES.get(new Identifier(SpellEngineMod.ID, school.spellName()+"_silence"));
    }
    static {
        SILENCES = new HashMap<>();
        for(var entry: MagicSchool.values()) {
            SILENCES.put(new Identifier(SpellEngineMod.ID, entry.spellName()+"_silence"), new SilenceEffect(StatusEffectCategory.HARMFUL,10395294,entry));
        }
        SILENCES.put(new Identifier(SpellEngineMod.ID, "silence"), ALLSILENCE);
    }
}
