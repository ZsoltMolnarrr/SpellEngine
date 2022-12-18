package net.spell_engine.spellbinding;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

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
        // this.doTick();
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
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(matrices, i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
//        this.drawTexture(matrices, i + 59, j + 20, 0, this.backgroundHeight + (((ForgingScreenHandler)this.handler).getSlot(0).hasStack() ? 0 : 16), 110, 16);
//        if ((((ForgingScreenHandler)this.handler).getSlot(0).hasStack() || ((ForgingScreenHandler)this.handler).getSlot(1).hasStack()) && !((ForgingScreenHandler)this.handler).getSlot(2).hasStack()) {
//            this.drawTexture(matrices, i + 99, j + 45, this.backgroundWidth, 0, 28, 21);
//        }
    }

    @Override
    public void close() {
        super.close();
        System.out.println("CLOSING SpellBindingScreen");
    }
}
