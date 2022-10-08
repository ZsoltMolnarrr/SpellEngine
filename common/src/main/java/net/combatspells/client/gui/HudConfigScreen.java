package net.combatspells.client.gui;

import net.combatspells.client.CombatSpellsClient;
import net.combatspells.config.HudConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudConfigScreen extends Screen {
    private Screen previous;
    private boolean partConfigVisible = false;
    private final ArrayList<ClickableWidget> partButtons = new ArrayList();
    private final Map<Part, CheckboxWidget> checkBoxes = new HashMap<>();

    public HudConfigScreen(Screen previous) {
        super(Text.translatable("gui.combatspells.hud"));
        this.previous = previous;
    }

    private HudConfig config() {
        return CombatSpellsClient.hudConfig.currentConfig;
    }

    @Override
    protected void init() {
        var buttonWidth = 60;
        var padding = 5;
        var buttonHeight = 20;
        var centerX = (width / 2);
        var centerY = (height / 2);

        addDrawableChild(new ButtonWidget(5, 5, buttonHeight, buttonHeight, Text.translatable("x"), button -> {
            close();
        }));

        int y = centerY - 50;

        addDrawableChild(new ButtonWidget(centerX - padding - buttonWidth - (buttonWidth/2), y, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.parts"), button -> {
            this.toggleParts();
        }));
        addDrawableChild(new ButtonWidget(centerX - (buttonWidth/2), y, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.preset"), button -> {
            this.nextPreset();
        }));
        addDrawableChild(new ButtonWidget(centerX + padding + buttonWidth - (buttonWidth/2), y, buttonWidth, buttonHeight, Text.translatable("gui.combatspells.reset"), button -> {
            this.reset();
        }));

        setupPartButtons();
    }

    private void setupPartButtons() {
        var centerX = (width / 2);
        var centerY = (height / 2);
        var buttonWidth = 60;

        int y = centerY - 20;
        int x = centerX - (buttonWidth/2);
        var targetButtons = createPartAdjustmentButtons(Part.TARGET, x, y);
        targetButtons.forEach(this::addDrawableChild);
        partButtons.addAll(targetButtons);
        y += 28;
        var iconButtons = createPartAdjustmentButtons(Part.ICON, x, y);
        iconButtons.forEach(this::addDrawableChild);
        partButtons.addAll(iconButtons);
        y += 28;
        var sizeButtons = createBarSizeButtons(x, y);
        sizeButtons.forEach(this::addDrawableChild);
        partButtons.addAll(sizeButtons);

        setPartsVisibility(partConfigVisible);
    }

    enum Part { TARGET, ICON }
    enum Direction { LEFT, RIGHT, UP, DOWN }

    private List<ClickableWidget> createPartAdjustmentButtons(Part part, int x, int y) {
        var buttons = new ArrayList<ClickableWidget>();
        var buttonSize = 20;
        var spacing = 8;

        var checked = partData(part).visible;
        var checkBox = new CheckboxWidget(x, y, buttonSize, buttonSize, Text.of(""), checked);
        buttons.add(checkBox);
        checkBoxes.put(part, checkBox);

        x += buttonSize + spacing;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("←"), button -> {
            move(part, Direction.LEFT);
        }));
        x += buttonSize + spacing;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("↑"), button -> {
            move(part, Direction.UP);
        }));
        x += buttonSize + spacing;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("↓"), button -> {
            move(part, Direction.DOWN);
        }));
        x += buttonSize + spacing;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("→"), button -> {
            move(part, Direction.RIGHT);
        }));
        return buttons;
    }

    private List<ButtonWidget> createBarSizeButtons(int x, int y) {
        var buttons = new ArrayList<ButtonWidget>();
        var buttonSize = 20;
        var spacing = 8;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("-"), button -> {
            changeBarWidth(false);
        }));
        x += buttonSize + spacing;
        buttons.add(new ButtonWidget(x, y, buttonSize, buttonSize, Text.translatable("+"), button -> {
            changeBarWidth(true);
        }));
        return buttons;
    }

    private void move(Part partType, Direction direction) {
        var part = partData(partType);
        if (part != null) {
            Vec2f diff = Vec2f.ZERO;
            switch (direction) {
                case LEFT -> {
                    diff = new Vec2f(-1, 0);
                }
                case RIGHT -> {
                    diff = new Vec2f(1, 0);
                }
                case UP -> {
                    diff = new Vec2f(0, -1);
                }
                case DOWN -> {
                    diff = new Vec2f(0, 1);
                }
            }
            part.offset = part.offset.add(diff);
        }
    }

    private HudConfig.Part partData(Part partType) {
        switch (partType) {
            case TARGET -> {
                return config().target;
            }
            case ICON -> {
                return config().icon;
            }
        }
        assert true;
        return null;
    }

    private void changeBarWidth(boolean increase) {
        var diff = increase ? 1 : -1;
        var config = CombatSpellsClient.hudConfig.currentConfig;
        if (!increase && config.bar_width <= 0) {
            return;
        }
        config.bar_width += diff;
    }

    private boolean partsVisible() {
        return partConfigVisible;
    }

    private void toggleParts() {
        setPartsVisibility(!partConfigVisible);
    }

    private void setPartsVisibility(boolean visibility) {
        partConfigVisible = visibility;
        for(var button: partButtons) {
            button.visible = partConfigVisible;
        }
    }

    public void close() {
        this.save();
        this.client.setScreen(previous);
    }

    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        HudRenderHelper.render(matrices, delta, true);
        super.render(matrices, mouseX, mouseY, delta);
        if (partsVisible()) {
            var bigButtonWidth = 60;
            var centerX = (width / 2);
            var centerY = (height / 2);
            var lineSpacing = 28;
            int x = centerX - (bigButtonWidth/2) - 8;
            int y = centerY - 20 + 6;
            rightAlignedText(matrices, x, y, "gui.combatspells.target");
            y += lineSpacing;
            rightAlignedText(matrices, x, y, "gui.combatspells.icon");
            y += lineSpacing;
            rightAlignedText(matrices, x, y, "gui.combatspells.bar_width");
        }
        for (var entry: checkBoxes.entrySet()) {
            partData(entry.getKey()).visible = entry.getValue().isChecked();
        }
    }

    private void rightAlignedText(MatrixStack matrices, int x, int y, String text) {
        var translated = I18n.translate(text);
        var width = textRenderer.getWidth(translated);
        textRenderer.draw(matrices, translated, x - width, y, 0xFFFFFF);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        var result = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        if (!this.isDragging() && button == 0) {
            var config = CombatSpellsClient.hudConfig.currentConfig;
            config.base.offset = new Vec2f(
                    (float) (config.base.offset.x + deltaX),
                    (float) (config.base.offset.y + deltaY));
        }
        return result;
    }

    public void nextPreset() {
        var config = CombatSpellsClient.hudConfig.currentConfig;
        HudElement.Origin origin;
        try {
            origin = HudElement.Origin.values()[(config.base.origin.ordinal() + 1)];
            CombatSpellsClient.hudConfig.currentConfig = HudConfig.preset(origin);
        } catch (Exception e) {
            origin = HudElement.Origin.values()[0];
            CombatSpellsClient.hudConfig.currentConfig = HudConfig.preset(origin);
        }
        refreshPartButtons();
    }

    public void save() {
        CombatSpellsClient.hudConfig.save();
    }

    public void reset() {
        CombatSpellsClient.hudConfig.currentConfig = HudConfig.createDefault();
        refreshPartButtons();
    }

    private void refreshPartButtons() {
        for(var partButton: partButtons) {
            remove(partButton);
        }
        partButtons.clear();
        setupPartButtons();
    }
}