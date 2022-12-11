package net.spell_engine.config;

import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.spell_engine.SpellEngineMod;

@Config(name = SpellEngineMod.ID)
public class ServerConfigWrapper extends PartitioningSerializer.GlobalData {
    @ConfigEntry.Category("server")
    @ConfigEntry.Gui.Excluded
    public ServerConfig server = new ServerConfig();
}
