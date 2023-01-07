package net.spell_engine.client.gui;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.spell_engine.config.ClientConfigWrapper;

public class ConfigMenuScreen extends Screen {
    private Screen previous;

    public ConfigMenuScreen(Screen parent) {
        super(Text.translatable("gui.spell_engine.config_menu"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        var buttonWidth = 120;
        var buttonHeight = 20;
        var buttonCenterX = (width / 2) - (buttonWidth / 2);
        var buttonCenterY = (height / 2) - (buttonHeight / 2);

        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY - 30, buttonWidth, buttonHeight, Text.translatable("gui.spell_engine.close"), button -> {
            close();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY, buttonWidth, buttonHeight, Text.translatable("gui.spell_engine.settings"), button -> {
            client.setScreen(AutoConfig.getConfigScreen(ClientConfigWrapper.class, this).get());
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY + 30, buttonWidth, buttonHeight, Text.translatable("gui.spell_engine.hud"), button -> {
            client.setScreen(new HudConfigScreen(this));
        }));
    }

    public void close() {
        this.client.setScreen(previous);
    }

    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
