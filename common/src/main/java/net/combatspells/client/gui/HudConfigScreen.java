package net.combatspells.client.gui;

import net.combatspells.client.CombatSpellsClient;
import net.combatspells.config.HudConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;

public class HudConfigScreen extends Screen {
    private Screen previous;

    public HudConfigScreen(Screen previous) {
        super(Text.translatable("gui.combatspells.hud"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        var buttonWidth = 120;
        var buttonHeight = 20;
        var buttonCenterX = (width / 2) - (buttonWidth / 2);
        var buttonCenterY = (height / 2) - (buttonHeight / 2);

        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY - 30, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.close"), button -> {
            close();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.corner"), button -> {
            nextOrigin();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY + 30, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.reset"), button -> {
            reset();
        }));
    }

    public void close() {
        this.save();
        this.client.setScreen(previous);
    }

    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        HudRenderHelper.render(matrices, delta, true);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.isDragging() && button == 0) {
            var config = CombatSpellsClient.hudConfig.currentConfig;
            config.castWidget.offset = new Vec2f(
                    (float) (config.castWidget.offset.x + deltaX),
                    (float) (config.castWidget.offset.y + deltaY));
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public static void nextOrigin() {
        var config = CombatSpellsClient.hudConfig.currentConfig;
        HudElement.Origin origin;
        try {
            origin = HudElement.Origin.values()[(config.castWidget.origin.ordinal() + 1)];
            config.castWidget = new HudElement(origin, origin.initialOffset());
        } catch (Exception e) {
            origin = HudElement.Origin.values()[0];
            config.castWidget = new HudElement(origin, origin.initialOffset());
        }
    }

    public void save() {
        CombatSpellsClient.hudConfig.save();
    }

    public void reset() {
        var config = CombatSpellsClient.hudConfig.currentConfig;
        config.castWidget = HudConfig.createDefaultCastWidget();
    }
}