package net.spell_engine.api.config;

import java.util.LinkedHashMap;

public class ConfigFile {
    public static class Equipment {
        public LinkedHashMap<String, WeaponConfig> weapons = new LinkedHashMap<>();
        public LinkedHashMap<String, ArmorSetConfig> armor_sets = new LinkedHashMap<>();
    }

    public static class Shields {
        public LinkedHashMap<String, ShieldConfig> shields = new LinkedHashMap<>();
    }

    public static class Effects {
        public LinkedHashMap<String, EffectConfig> effects = new LinkedHashMap<>();
    }
}
