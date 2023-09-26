package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "client")
public class ClientConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastChannelled = true;
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastCharged = true;
    @ConfigEntry.Gui.Tooltip
    public boolean useKeyHighPriority = true;

    @ConfigEntry.Gui.Tooltip
    public boolean highlightTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean stickyTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean filterInvalidTargets = true;
    @ConfigEntry.Gui.Tooltip
    public boolean alwaysShowFullTooltip = false;
    @ConfigEntry.Gui.Tooltip
    public boolean showSpellBindingTooltip = true;
    @ConfigEntry.Gui.Tooltip
    public boolean showSpellCastErrors = true;
    @ConfigEntry.Gui.Tooltip
    public boolean shoulderSurfingAdaptiveWhileUse = true;
}
