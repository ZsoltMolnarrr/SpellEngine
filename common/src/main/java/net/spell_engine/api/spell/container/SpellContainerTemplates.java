package net.spell_engine.api.spell.container;

import net.spell_engine.SpellEngineMod;
import net.tiny_config.ConfigManager;

import java.util.List;

public class SpellContainerTemplates {
    public static class Config {
        public SpellContainer spell_book = new SpellContainer(SpellContainer.ContentType.NONE, "", null, "", 0, List.of());
    }

    public static Config defaults() {
        return new Config();
    }

    public static ConfigManager<Config> config = new ConfigManager<>
            ("spell_container_templates_v2", defaults())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();
}
