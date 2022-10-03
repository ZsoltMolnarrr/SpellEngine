package net.combatspells.config;

import net.combatspells.client.gui.HudElement;
import net.minecraft.util.math.Vec2f;

public class HudConfig {
    public HudElement castWidget;

    public static HudConfig createDefault() {
        var config = new HudConfig();
        config.castWidget = createDefaultRollWidget();
        return config;
    }

    public static HudElement createDefaultRollWidget() {
        var origin = HudElement.Origin.BOTTOM;
        var offset = origin.initialOffset().add(new Vec2f(0, -40));
        return new HudElement(origin, offset);
    }
}