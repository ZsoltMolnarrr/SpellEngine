package net.spell_engine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.runes.RuneCraftingScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class RuneCraftingScreen extends ForgingScreen<RuneCraftingScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(SpellEngineMod.MOD_ID, "textures/gui/rune_crafting.png");

    public RuneCraftingScreen(RuneCraftingScreenHandler handler, PlayerInventory playerInventory, Text title) {
        super(handler, playerInventory, title, TEXTURE);
        this.titleX = 60;
        this.titleY = 18;
    }

    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        RenderSystem.disableBlend();
        super.drawForeground(matrices, mouseX, mouseY);
    }
}
