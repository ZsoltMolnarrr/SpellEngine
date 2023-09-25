package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "client")
public class ClientConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean autoRelease = true;
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastChannelled = true;
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastCharged = false;

    @ConfigEntry.Gui.Tooltip
    public boolean highlightTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean stickyTarget = true;
    @ConfigEntry.Gui.Tooltip
    public boolean filterInvalidTargets = true;

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
    public boolean lockHotbarOnRightClick = false;
    @ConfigEntry.Gui.Tooltip
    public boolean unlockHotbarOnEscape = false;
    @ConfigEntry.Gui.Tooltip
    public boolean alwaysShowFullTooltip = false;
    @ConfigEntry.Gui.Tooltip
    public boolean showSpellBindingTooltip = true;
    @ConfigEntry.Gui.Tooltip
    public boolean showSpellCastErrors = true;
    @ConfigEntry.Gui.Tooltip
    public boolean shoulderSurfingAdaptiveWhileUse = true;
}
