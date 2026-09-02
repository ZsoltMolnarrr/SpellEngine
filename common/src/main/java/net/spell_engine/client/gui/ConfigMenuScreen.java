package net.spell_engine.client.gui;

import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.spell_engine.config.ClientConfigWrapper;

public class ConfigMenuScreen extends Screen {
    private Screen previous;

    public ConfigMenuScreen(Screen parent) {
        super(Component.translatable("gui.spell_engine.config_menu"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        var buttonWidth = 120;
        var buttonHeight = 20;
        var buttonCenterX = (width / 2) - (buttonWidth / 2);
        var buttonCenterY = (height / 2) - (buttonHeight / 2);

        addRenderableWidget(
                Button.builder(Component.translatable("gui.spell_engine.close"), button -> { onClose(); })
                        .pos(buttonCenterX, buttonCenterY - 30)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
        addRenderableWidget(
                Button.builder(Component.translatable("gui.spell_engine.settings"), button -> {
                    minecraft.gui.setScreen(AutoConfigClient.getConfigScreen(ClientConfigWrapper.class, this).get());
                })
                .pos(buttonCenterX, buttonCenterY)
                .size(buttonWidth, buttonHeight)
                .build()
        );
        addRenderableWidget(
                Button.builder(Component.translatable("gui.spell_engine.hud"), button -> {
                            minecraft.gui.setScreen(new HudConfigScreen(this));
                        })
                        .pos(buttonCenterX, buttonCenterY + 30)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
    }

    public void onClose() {
        this.minecraft.gui.setScreen(previous);
    }

}
