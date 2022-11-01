package net.combatspells.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "server")
public class ServerConfig implements ConfigData {
    @Comment("Spell haste reduces the cooldown time of abilities")
    public boolean haste_affects_cooldown = true;
    @Comment("Spell costs exhausts (hunger) will be multiplied with this value. Set `0` for no exhaust.")
    public float spell_cost_exhaust_multiplier = 1F;
    @Comment("Spells should cost items. Set `false` to remove runes cost from spells.")
    public boolean spell_cost_item_allowed = true;
    @Comment("Spells should damage items on use. Set `false` to disable.")
    public boolean spell_cost_durability_allowed = true;
}
