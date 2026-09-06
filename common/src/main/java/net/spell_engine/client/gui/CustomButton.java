package net.spell_engine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.spell_engine.SpellEngineMod;

public class CustomButton extends ButtonWidget {

    private static final Identifier BUTTONS_TEXTURE = new Identifier(SpellEngineMod.ID, "textures/gui/buttons.png");
    private int u;
    private int v;
    private int stateOffsetY;

    public CustomButton(int x, int y, Type type, PressAction onPress) {
        super(x, y, type.width(), type.height(), Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.u = type.u();
        this.v = type.v();
        this.stateOffsetY = type.stateOffsetY();
    }

    public enum Type {
        SMALL_UP,
        SMALL_DOWN
        ;

        private Drawable.DrawRect rect() {
            return switch (this) {
                case SMALL_UP -> new Drawable.DrawRect(0, 0, 11, 7);
                case SMALL_DOWN -> new Drawable.DrawRect(16, 0, 11, 7);
            };
        }

        public int stateOffsetY() {
            return switch (this) {
                case SMALL_UP, SMALL_DOWN -> 16;
            };
        }

        public int width() {
            return rect().width();
        }

        public int height() {
            return rect().height();
        }

        public int u() {
            return rect().u();
        }

        public int v() {
            return rect().v();
        }
    }

    private int getTextureY() {
        int i = 0;
        if (!this.active) {
            i = 2;
        } else if (this.isHovered()) {
            i = 1;
        }
        return v + i * stateOffsetY;
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        context.setShaderColor(1.0f, 1.0f, 1.0f, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        context.drawTexture(BUTTONS_TEXTURE, this.getX(), this.getY(), this.u, this.getTextureY(), this.getWidth(), this.getHeight());
        // context.drawNineSlicedTexture(BUTTONS_TEXTURE, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 20, 4, 200, 20, 0, this.getTextureY());
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        int i = this.active ? 0xFFFFFF : 0xA0A0A0;
        // this.drawMessage(context, minecraftClient.textRenderer, i | MathHelper.ceil(this.alpha * 255.0f) << 24);
    }
}
