package net.spell_engine.config;

import net.spell_engine.api.spell.Spell;

import java.util.LinkedHashMap;
import java.util.List;

public class WeaknessConfig {
    public WeaknessConfig() {}
    public LinkedHashMap<String, List<Spell.Impact.TargetModifier>> school_weaknesses = new LinkedHashMap<>();
    public boolean isValid() {
        return school_weaknesses != null;
    }
}
