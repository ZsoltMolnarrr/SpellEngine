package net.spell_engine.config;

import net.spell_engine.api.spell.weakness.ScopedWeakness;

import java.util.LinkedHashMap;
import java.util.List;

public class WeaknessConfig {
    public WeaknessConfig() {}
    public LinkedHashMap<String, List<ScopedWeakness>> school_weaknesses = new LinkedHashMap<>();
    public boolean isValid() {
        return school_weaknesses != null;
    }
}
