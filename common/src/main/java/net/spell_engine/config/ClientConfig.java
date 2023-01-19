package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "client")
public class ClientConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean autoRelease = true;

//    @ConfigEntry.Gui.Tooltip
//    public boolean showTargetNameWhenMultiple = false;

    @ConfigEntry.Gui.Tooltip
    public boolean highlightTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean stickyTarget = true;

//    @ConfigEntry.Gui.Tooltip
//    public boolean useMagicColorForHighlight = true;
    @ConfigEntry.Gui.Tooltip
    public boolean restartCastingWhenSwitchingSpell = false;
    @ConfigEntry.Gui.Tooltip
    public boolean collapseSpellHotbar = true;
    @ConfigEntry.Gui.Tooltip
    public boolean collapsedIndicators = true;
    @ConfigEntry.Gui.Tooltip
    public boolean indicateActiveHotbar = true;
    @ConfigEntry.Gui.Tooltip
    public boolean showFocusedHotbarOnly = false;
    @ConfigEntry.Gui.Tooltip
    public boolean lockHotbarOnRightClick = true;
    @ConfigEntry.Gui.Tooltip
    public boolean unlockHotbarOnEscape = true;
    @ConfigEntry.Gui.Tooltip
    public boolean alwaysShowFullTooltip = false;
    @ConfigEntry.Gui.Tooltip
    public boolean showSpellBindingTooltip = true;
}
