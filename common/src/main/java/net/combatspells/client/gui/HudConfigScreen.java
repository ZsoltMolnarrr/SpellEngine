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

        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY - 60, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.close"), button -> {
            close();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY - 30, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.preset"), button -> {
            nextPreset();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.reset"), button -> {
            reset();
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX, buttonCenterY + 30, buttonHeight, buttonHeight, Text.translatable("-"), button -> {
            changeWidth(false);
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX + 30, buttonCenterY + 30, buttonHeight, buttonHeight, Text.translatable("+"), button -> {
            changeWidth(true);
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX + 60, buttonCenterY + 30, buttonHeight, buttonHeight, Text.translatable("↑"), button -> {
            moveTargetText(true);
        }));
        addDrawableChild(new ButtonWidget(buttonCenterX + 90, buttonCenterY + 30, buttonHeight, buttonHeight, Text.translatable("↓"), button -> {
            moveTargetText(false);
        }));
    }

    private void moveTargetText(boolean up) {
        var diff = up ? -1 : 1;
        var config = CombatSpellsClient.hudConfig.currentConfig;
        config.target_offset = config.target_offset.add(new Vec2f(0, diff));
    }

    private void changeWidth(boolean increase) {
        var diff = increase ? 1 : -1;
        var config = CombatSpellsClient.hudConfig.currentConfig;
        config.bar_width += diff;
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
            config.base.offset = new Vec2f(
                    (float) (config.base.offset.x + deltaX),
                    (float) (config.base.offset.y + deltaY));
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public static void nextPreset() {
        var config = CombatSpellsClient.hudConfig.currentConfig;
        HudElement.Origin origin;
        try {
            origin = HudElement.Origin.values()[(config.base.origin.ordinal() + 1)];
            config.base = HudConfig.preset(origin);
        } catch (Exception e) {
            origin = HudElement.Origin.values()[0];
            config.base = HudConfig.preset(origin);
        }
    }

    public void save() {
        CombatSpellsClient.hudConfig.save();
    }

    public void reset() {
        CombatSpellsClient.hudConfig.currentConfig = HudConfig.createDefault();
    }
}