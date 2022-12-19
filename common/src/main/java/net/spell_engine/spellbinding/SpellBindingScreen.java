package net.spell_engine.spellbinding;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.util.SpellRender;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Environment(value= EnvType.CLIENT)
public class SpellBindingScreen extends HandledScreen<SpellBindingScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(SpellEngineMod.ID, "textures/gui/" + SpellBinding.name + ".png");

    private ItemStack stack;

    public SpellBindingScreen(SpellBindingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.stack = ItemStack.EMPTY;
    }

    protected void init() {
        super.init();
    }

    public void handledScreenTick() {
        super.handledScreenTick();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        delta = this.client.getTickDelta();
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        DiffuseLighting.disableGuiDepthLighting();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int originX = (this.width - this.backgroundWidth) / 2;
        int originY = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(matrices, originX, originY, 0, 0, this.backgroundWidth, this.backgroundHeight);
        DiffuseLighting.enableGuiDepthLighting();
        this.updateButtons(originX, originY);
        this.drawButtons(matrices, mouseX, mouseY);
    }

    private List<ButtonViewModel> buttonViewModels = List.of();

    private static final int BUTTONS_ORIGIN_X = 60;
    private static final int BUTTONS_ORIGIN_Y = 14;

    private void updateButtons(int originX, int originY) {
        var buttons = new ArrayList<ButtonViewModel>();
        for (int i = 0; i < SpellBindingScreenHandler.MAXIMUM_SPELL_COUNT; i++) {
            var rawId = handler.spellId[i];
            var cost = handler.spellCost[i];
            var requirement = handler.spellLevelRequirement[i];
            var spellId = SpellRegistry.fromRawId(rawId);
            if (spellId.isEmpty()) { continue; }
            var id = spellId.get();
            var spell = new SpellInfo(
                    SpellRender.iconTexture(id),
                    Text.translatable(SpellTooltip.spellTranslationKey(id)),
                    cost, requirement);
            var button = new ButtonViewModel(
                    originX + BUTTONS_ORIGIN_X, originY + BUTTONS_ORIGIN_Y + (buttons.size() * BUTTON_HEIGHT),
                    BUTTON_WIDTH, BUTTON_HEIGHT,
                    true, spell);
            buttons.add(button);
        }
        buttonViewModels = buttons;
    }

    private void drawButtons(MatrixStack matrices, int mouseX, int mouseY) {
        for(var button: buttonViewModels) {
            var state = button.mouseOver(mouseX, mouseY) ? ButtonState.HOVER : ButtonState.NORMAL;
            drawSpellButton(matrices, button, state);
        }
    }

    enum ButtonState { NORMAL, HOVER }
    record SpellInfo(Identifier icon, Text name, int cost, int requirement) { }
    record ButtonViewModel(int x, int y, int width, int height, boolean isEnabled, SpellInfo info) {
        public boolean mouseOver(int mouseX, int mouseY) {
            return (mouseX > x && mouseX < x + width) && (mouseY > y && mouseY < y + height);
        }
    }

    private static final int BUTTON_TEXTURE_U = 0;
    private static final int BUTTON_TEXTURE_V = 166;
    private static final int BUTTON_WIDTH = 108;
    private static final int BUTTON_HEIGHT = 19;
    private static final int SPELL_ICON_SIZE = 16;
    private static final int SPELL_ICON_INDENT = (int) Math.ceil((BUTTON_HEIGHT - SPELL_ICON_SIZE) / 2.0);
    private void drawSpellButton(MatrixStack matrices, ButtonViewModel viewModel, ButtonState state) {
        int u = BUTTON_TEXTURE_U;
        int v = BUTTON_TEXTURE_V;
        if (viewModel.isEnabled) {
            switch (state) {
                case NORMAL -> {
                    v += 0;
                }
                case HOVER -> {
                    v += viewModel.height * 2;
                }
            }
        } else {
            v += viewModel.height;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        drawTexture(matrices, viewModel.x, viewModel.y, u, v, viewModel.width, viewModel.height);
        if (viewModel.info != null) {
            var spell = viewModel.info;
            textRenderer.drawWithShadow(matrices, spell.name,
                    viewModel.x + viewModel.height, viewModel.y + SPELL_ICON_INDENT, 0xFFFFFF);
            if (spell.icon != null) {
                RenderSystem.setShaderTexture(0, spell.icon);
                // int x, int y, int u, int v, int width, int height
                DrawableHelper.drawTexture(matrices,
                        viewModel.x + SPELL_ICON_INDENT,
                        viewModel.y + SPELL_ICON_INDENT,
                        0, 0,
                        SPELL_ICON_SIZE, SPELL_ICON_SIZE, SPELL_ICON_SIZE, SPELL_ICON_SIZE);
//                drawTexture(matrices,
//                        viewModel.x + SPELL_ICON_INDENT, viewModel.x + SPELL_ICON_INDENT,
//                        0, 0,
//                        SPELL_ICON_SIZE, SPELL_ICON_SIZE);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        System.out.println("CLOSING SpellBindingScreen");
    }
}
