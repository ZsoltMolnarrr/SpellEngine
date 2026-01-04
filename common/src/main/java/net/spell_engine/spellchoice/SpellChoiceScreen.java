package net.spell_engine.spellchoice;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Environment(value = EnvType.CLIENT)
public class SpellChoiceScreen extends Screen {
    private final ItemStack itemStack;

    public SpellChoiceScreen(ItemStack itemStack) {
        super(Text.literal("Spell Choice"));
        this.itemStack = itemStack;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
//        // Render dark background overlay
//        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Draw item name centered on screen
        if (!itemStack.isEmpty()) {
            var itemName = itemStack.getName();
            int textX = (this.width - this.textRenderer.getWidth(itemName)) / 2;
            int textY = this.height / 2;
            context.drawText(this.textRenderer, itemName, textX, textY, 0xFFFFFF, true);
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // Don't pause game
    }
}
