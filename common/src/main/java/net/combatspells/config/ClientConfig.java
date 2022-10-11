package net.combatspells.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "client")
public class ClientConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean autoRelease = true;
    @ConfigEntry.Gui.Tooltip
    public boolean highlightTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean useMagicColorForHighlight = true;
}
