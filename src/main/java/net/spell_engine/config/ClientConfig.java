package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.spell_engine.client.input.WrappedKeybinding;
import org.jetbrains.annotations.Nullable;

@Config(name = "client")
public class ClientConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastChannelled = true;
    @ConfigEntry.Gui.Tooltip
    public boolean holdToCastCharged = true;
    @ConfigEntry.Gui.Tooltip
    @Nullable
    public WrappedKeybinding.VanillaAlternative spellHotbar_1_defer = WrappedKeybinding.VanillaAlternative.USE_KEY;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_2_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_2;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_3_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_3;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_4_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_4;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_5_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_5;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_6_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_6;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_7_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_7;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_8_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_8;
    @ConfigEntry.Gui.Tooltip
    public WrappedKeybinding.VanillaAlternative spellHotbar_9_defer = WrappedKeybinding.VanillaAlternative.HOTBAR_KEY_9;


    @ConfigEntry.Gui.Tooltip
    public boolean useKeyHighPriority = false;
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
