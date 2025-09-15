package net.spell_engine.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class Drawable {
    public enum Anchor { LEADING, TRAILING, CENTER }
    public record Component(DrawRect draw, Texture texture) {
        public void draw(DrawContext context, int x, int y, Anchor hAnchor, Anchor vAnchor) {
            switch (hAnchor) {
                case LEADING -> {
                    x = x;
                }
                case CENTER -> {
                    x -= draw().width / 2;
                }
                case TRAILING -> {
                    x -= draw().width;
                }
            }
            switch (vAnchor) {
                case LEADING -> {
                    y = y;
                }
                case CENTER -> {
                    y -= draw().height / 2;
                }
                case TRAILING -> {
                    y -= draw().height;
                }
            }
            context.drawTexture(texture().id, x, y, draw().u, draw().v, draw().width, draw().height, texture().width, texture().height);
        }

        public void drawFlexibleWidth(DrawContext context, int x, int y, int width, Anchor vAnchor) {
            switch (vAnchor) {
                case LEADING -> {
                    y = y;
                }
                case CENTER -> {
                    y -= draw().height / 2;
                }
                case TRAILING -> {
                    y -= draw().height;
                }
            }
            context.drawTexture(texture().id, x, y, draw().u, draw().v, width, draw().height, texture().width, texture().height);
        }
    }
    public record DrawRect(int u, int v, int width, int height) {}
    public record Texture(Identifier id, int width, int height) {}
}
