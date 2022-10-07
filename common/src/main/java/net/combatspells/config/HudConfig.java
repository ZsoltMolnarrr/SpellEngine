package net.combatspells.config;

import net.combatspells.client.gui.HudElement;
import net.minecraft.util.math.Vec2f;

public class HudConfig {
    public HudElement base;
    public Part target;
    public Part icon;
    public int bar_width;

    public static class Part { public Part() { }
        public boolean visible = true;
        public Vec2f offset = Vec2f.ZERO;
        public Part(boolean visible, Vec2f offset) {
            this.visible = visible;
            this.offset = offset;
        }
    }

    public static HudConfig createDefault() {
        var defaultWidth = 90;
        var config = new HudConfig();
        config.base = createDefaultCastWidget();
        config.target = new Part(true, new Vec2f(0, -12));
        config.icon = new Part(true, new Vec2f((defaultWidth / 2) + 10, -6));
        config.bar_width = defaultWidth;
        return config;
    }

    public static HudElement createDefaultCastWidget() {
        var hudElement = preset(HudElement.Origin.BOTTOM);
        hudElement.offset = hudElement.offset.add(new Vec2f(0, -50));
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