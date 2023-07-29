package net.spell_engine.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.config.HudConfig;

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
        super(Text.translatable("gui.spell_engine.hud"));
        this.previous = previous;
    }

    private HudConfig config() {
        return SpellEngineClient.hudConfig.value;
    }

    @Override
    protected void init() {
        var buttonWidth = 60;
        var padding = 5;
        var buttonHeight = 20;
        var centerX = (width / 2);
        var centerY = (height / 2);



        addDrawableChild(
                ButtonWidget.builder(Text.translatable("x"), button -> { close(); })
                        .position(5, 5)
                        .size(buttonHeight, buttonHeight)
                        .build()
        );

        int y = centerY - 50;

        addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.spell_engine.parts"), button -> { this.toggleParts(); })
                        .position(centerX - padding - buttonWidth - (buttonWidth/2), y)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
        addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.spell_engine.preset"), button -> { this.nextPreset(); })
                        .position(centerX - (buttonWidth/2), y)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
        addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.spell_engine.reset"), button -> { this.reset(); })
                        .position(centerX + padding + buttonWidth - (buttonWidth/2), y)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );

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
        addDrawableChild(
                ButtonWidget.builder(Text.of("←"), button -> { move(part, Direction.LEFT); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
        x += buttonSize + spacing;
        addDrawableChild(
                ButtonWidget.builder(Text.of("↑"), button -> { move(part, Direction.UP); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
        x += buttonSize + spacing;
        addDrawableChild(
                ButtonWidget.builder(Text.of("↓"), button -> { move(part, Direction.DOWN); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
        x += buttonSize + spacing;
        addDrawableChild(
                ButtonWidget.builder(Text.of("→"), button -> { move(part, Direction.RIGHT); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
        return buttons;
    }

    private List<ButtonWidget> createBarSizeButtons(int x, int y) {
        var buttons = new ArrayList<ButtonWidget>();
        var buttonSize = 20;
        var spacing = 8;
        addDrawableChild(
                ButtonWidget.builder(Text.of("-"), button -> { changeBarWidth(false); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
        x += buttonSize + spacing;
        addDrawableChild(
                ButtonWidget.builder(Text.of("+"), button -> { changeBarWidth(true); })
                        .position(x, y)
                        .size(buttonSize, buttonSize)
                        .build()
        );
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
                return config().castbar.target;
            }
            case ICON -> {
                return config().castbar.icon;
            }
        }
        assert true;
        return null;
    }

    private void changeBarWidth(boolean increase) {
        var diff = increase ? 1 : -1;
        var config = SpellEngineClient.hudConfig.value;
        if (!increase && config.castbar.width <= 0) {
            return;
        }
        config.castbar.width += diff;
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

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        HudRenderHelper.render(context, delta, true);
        super.render(context, mouseX, mouseY, delta);
        if (partsVisible()) {
            var bigButtonWidth = 60;
            var centerX = (width / 2);
            var centerY = (height / 2);
            var lineSpacing = 28;
            int x = centerX - (bigButtonWidth/2) - 8;
            int y = centerY - 20 + 6;
            rightAlignedText(context, x, y, "gui.spell_engine.target");
            y += lineSpacing;
            rightAlignedText(context, x, y, "gui.spell_engine.icon");
            y += lineSpacing;
            rightAlignedText(context, x, y, "gui.spell_engine.bar_width");
        }
        for (var entry: checkBoxes.entrySet()) {
            partData(entry.getKey()).visible = entry.getValue().isChecked();
        }
    }

    private void rightAlignedText(DrawContext context, int x, int y, String text) {
        var translated = I18n.translate(text);
        var width = textRenderer.getWidth(translated);
        context.drawText(textRenderer, translated, x - width, y, 0xFFFFFF, false);
    }

    private Dragged dragged;
    private enum Dragged {
        CAST_BAR, HOT_BAR, ERROR_MESSAGE
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HudRenderHelper.CastBarWidget.lastRendered != null && HudRenderHelper.CastBarWidget.lastRendered.contains(mouseX, mouseY)) {
            dragged = Dragged.CAST_BAR;
            return true;
        }
        if (HudRenderHelper.SpellHotBarWidget.lastRendered != null && HudRenderHelper.SpellHotBarWidget.lastRendered.contains(mouseX, mouseY)) {
            dragged = Dragged.HOT_BAR;
            return true;
        }
        if (HudRenderHelper.ErrorMessageWidget.lastRendered != null && HudRenderHelper.ErrorMessageWidget.lastRendered.contains(mouseX, mouseY)) {
            dragged = Dragged.ERROR_MESSAGE;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragged = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        var result = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        if (!this.isDragging() && button == 0 && dragged != null) {
            var config = SpellEngineClient.hudConfig.value;
            switch (dragged) {
                case CAST_BAR -> {
                    config.castbar.base.offset = new Vec2f(
                            (float) (config.castbar.base.offset.x + deltaX),
                            (float) (config.castbar.base.offset.y + deltaY));

                }
                case HOT_BAR -> {
                    config.hotbar.offset = new Vec2f(
                            (float) (config.hotbar.offset.x + deltaX),
                            (float) (config.hotbar.offset.y + deltaY));
                }
                case ERROR_MESSAGE -> {
                    config.error_message.offset = new Vec2f(
                            (float) (config.error_message.offset.x + deltaX),
                            (float) (config.error_message.offset.y + deltaY));
                }
            }
        }
        return result;
    }

    private int lastSelectedPreset = 0;

    public void nextPreset() {
        lastSelectedPreset += 1;
        try {
            SpellEngineClient.hudConfig.value = HudConfig.presets.get(lastSelectedPreset).copy();
        } catch (Exception e) {
            lastSelectedPreset = 0;
            SpellEngineClient.hudConfig.value = HudConfig.presets.get(0).copy();
        }
        refreshPartButtons();
    }

    public void save() {
        SpellEngineClient.hudConfig.save();
    }

    public void reset() {
        SpellEngineClient.hudConfig.value = HudConfig.createDefault();
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