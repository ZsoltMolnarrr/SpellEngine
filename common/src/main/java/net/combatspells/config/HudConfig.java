package net.combatspells.config;

import net.combatspells.client.gui.HudElement;
import net.minecraft.util.math.Vec2f;

public class HudConfig {
    public HudElement base;
    public Vec2f target_offset;
    public int bar_width;

    public static HudConfig createDefault() {
        var config = new HudConfig();
        config.base = createDefaultCastWidget();
        config.target_offset = new Vec2f(0, -12);
        config.bar_width = 90;
        return config;
    }

    public static HudElement createDefaultCastWidget() {
        var hudElement = preset(HudElement.Origin.BOTTOM);
        hudElement.offset = hudElement.offset.add(new Vec2f(0, -32));
        return hudElement;
    }

    public static HudElement preset(HudElement.Origin origin) {
        int offsetW = 70;
        int offsetH = 16;
        var offset = new Vec2f(0, 0);
        switch (origin) {
            case TOP -> {
                offset = new Vec2f(0, offsetH * 2);
            }
            case TOP_LEFT -> {
                offset = new Vec2f(offsetW - 8, offsetH * 2);
            }
            case TOP_RIGHT -> {
                offset = new Vec2f((-1) * offsetW - 8, offsetH * 2);
            }
            case BOTTOM -> {
                offset = new Vec2f(0, (-1) * offsetH);
            }
            case BOTTOM_LEFT -> {
                offset = new Vec2f(offsetW - 8, (-1) * offsetH);
            }
            case BOTTOM_RIGHT -> {
                offset = new Vec2f((-1) * offsetW - 8, (-1) * offsetH);
            }
        }
        return new HudElement(origin, offset);
    }
}