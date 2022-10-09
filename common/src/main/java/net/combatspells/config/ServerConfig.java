package net.combatspells.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "server")
public class ServerConfig implements ConfigData {
    @Comment("Spell haste reduces the cooldown time of abilities")
    public boolean haste_affects_cooldown = true;
}
