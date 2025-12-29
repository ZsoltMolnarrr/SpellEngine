package net.spell_engine.spellbinding;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.List;

public class SpellBindingWidgets {
    private static final Identifier Pl = Identifier.of(SpellEngineMod.ID, "textures/gui/" + SpellBinding.name + ".png");

    private static final int BUTTON_TEXTURE_U = 0;
    private static final int BUTTON_TEXTURE_V = 184;
    private static final int BUTTON_WIDTH = 108;
    private static final int BUTTON_HEIGHT = 24;
    static final int SPELL_ICON_SIZE = 16;
    static final int SPELL_ICON_INDENT = (int) Math.ceil((BUTTON_HEIGHT - SPELL_ICON_SIZE) / 2.0);
    private static final int ORB_INDENT = 1;
    private static final int ORB_ICON_SIZE = 13;
    private static final int ORB_TEXTURE_U = 242;
    private static final int ORB_TEXTURE_V = 242;
    private static final int BOTTOM_TEXT_OFFSET = 10;
    private static final int COLOR_GOOD = 0x36ff00;
    private static final int COLOR_BAD = 0xfc5c5c;
    private static final int COLOR_GOOD_BUT_DISABLED = 0x48890e;
    // Tier row constants
    static final int TIER_ROW_HEIGHT = 24;
    static final int TIER_ROW_ICON_Y_OFFSET = (TIER_ROW_HEIGHT - SPELL_ICON_SIZE) / 2;
    static final int TIER_ROW_WIDTH = 108;
    static final int SELECTION_INDICATOR_SIZE = 24;
    static final int SELECTION_INDICATOR_U = 224;
    static final int SELECTION_INDICATOR_V = 0;

    record SpellViewModel(Identifier id, Identifier icon, Text name) { }

    // New tier-based view models
    record TierRowViewModel(
        int tier,
        boolean shown,
        int x, int y, int width, int height,
        List<SpellIconViewModel> spellIcons
    ) {
        public boolean mouseOver(int mouseX, int mouseY) {
            if(!shown) { return false; }
            return (mouseX > x && mouseX < x + width) && (mouseY > y && mouseY < y + height);
        }
    }

    record SpellIconViewModel(
        int originalIndex,
        int x, int y, int size,
        boolean isEnabled,
        boolean isDetailsPublic,
        SpellViewModel spell,
        SpellBinding.State binding
    ) {
        public boolean mouseOver(int mouseX, int mouseY) {
            return (mouseX >= x && mouseX < x + size) && (mouseY >= y && mouseY < y + size);
        }
    }

    record SpellBookViewModel(
        int originalIndex,
        boolean shown,
        int x, int y, int width, int height,
        boolean isEnabled,
        Item item,
        SpellBinding.State binding
    ) {
        public boolean mouseOver(int mouseX, int mouseY) {
            if (!shown) { return false; }
            return (mouseX >= x && mouseX < x + width) && (mouseY >= y && mouseY < y + height);
        }
    }

    public static void drawSpellIcon(DrawContext context, SpellBindingWidgets.SpellIconViewModel icon, int mouseX, int mouseY) {
        boolean mouseOver = icon.mouseOver(mouseX, mouseY);
        boolean alreadyApplied = icon.binding.state == SpellBinding.State.ApplyState.ALREADY_APPLIED;
        float alpha = (icon.isEnabled || alreadyApplied) ? 1.0f : 0.5f;

        // Draw subtle highlight on hover
        if (mouseOver && icon.isEnabled) {
            context.fill(icon.x - 1, icon.y - 1,
                    icon.x + icon.size + 1, icon.y + icon.size + 1,
                    0x40FFFFFF);
        }

        // Draw spell icon or item icon
        context.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.enableBlend();

        if (icon.spell != null && icon.spell.icon != null) {
            context.drawTexture(icon.spell.icon, icon.x, icon.y,
                    0, 0, icon.size, icon.size, icon.size, icon.size);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();

        // Draw selection indicator for already-bound spells
        if (alreadyApplied) {
            RenderSystem.enableBlend();
            int indicatorOffset = (SpellBindingWidgets.SELECTION_INDICATOR_SIZE - SpellBindingWidgets.SPELL_ICON_SIZE) / 2;
            context.drawTexture(Pl,
                    icon.x - indicatorOffset,
                    icon.y - indicatorOffset,
                    SpellBindingWidgets.SELECTION_INDICATOR_U,
                    SpellBindingWidgets.SELECTION_INDICATOR_V,
                    SpellBindingWidgets.SELECTION_INDICATOR_SIZE,
                    SpellBindingWidgets.SELECTION_INDICATOR_SIZE);
            RenderSystem.disableBlend();
        }
    }

    public static void drawSpellBook(DrawContext context, TextRenderer textRenderer, SpellBindingWidgets.SpellBookViewModel book, int mouseX, int mouseY) {
        if (!book.shown) { return; }  // Skip if not shown
        boolean mouseOver = book.mouseOver(mouseX, mouseY);
        boolean isUnlocked = book.isEnabled;

        // Draw hover highlight
        if (mouseOver && book.isEnabled) {
            context.fill(book.x - 1, book.y - 1,
                    book.x + book.width + 1, book.y + book.height + 1,
                    0x40FFFFFF);
        }

        // Draw book icon
        int iconX = book.x + SpellBindingWidgets.SPELL_ICON_INDENT;
        int iconY = book.y + SpellBindingWidgets.TIER_ROW_ICON_Y_OFFSET;

        context.setShaderColor(1.0f, 1.0f, 1.0f, isUnlocked ? 1.0f : 0.5f);
        RenderSystem.enableBlend();
        context.drawItem(book.item.getDefaultStack(), iconX, iconY);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();

        // Draw book name
        Text bookName = book.item.getName();
        int textX = iconX + SpellBindingWidgets.SPELL_ICON_SIZE + 4;  // 4px gap after icon
        int textY = book.y + (SpellBindingWidgets.TIER_ROW_HEIGHT - textRenderer.fontHeight) / 2;  // Vertically centered
        context.drawTextWithShadow(textRenderer, bookName, textX, textY,
                isUnlocked ? 0xFFFFFF : 0x808080);
    }
}
