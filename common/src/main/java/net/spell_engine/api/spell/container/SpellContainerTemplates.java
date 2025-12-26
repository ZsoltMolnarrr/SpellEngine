package net.spell_engine.api.spell.container;

import net.spell_engine.SpellEngineMod;
import net.tiny_config.ConfigManager;

import java.util.List;

public class SpellContainerTemplates {
    public static class Config {
        public SpellContainer spell_book;
    }

    public static Config defaults() {
        Config config = new Config();
        config.spell_book = new SpellContainer(null, false, null, "", 0, List.of(), true);
        return config;
    }

    public static ConfigManager<Config> config = new ConfigManager<>
            ("spell_container_templates_v2", defaults())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();
}
