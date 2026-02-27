package net.spell_engine.spellbinding.spellchoice;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.util.SpellRender;

import java.util.ArrayList;
import java.util.List;

@Environment(value = EnvType.CLIENT)
public class SpellChoiceScreen extends HandledScreen<SpellChoiceScreenHandler> {
    private static final int SPELL_ICON_SIZE = 16;
    private static final int ICON_SPACING = 16;

    private List<SpellIconViewModel> spellIcons = new ArrayList<>();

    public SpellChoiceScreen(SpellChoiceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, Text.empty());
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
        this.titleY = 20;
    }

    private List<SpellIconViewModel> createSpellIcons() {
        List<SpellIconViewModel> icons = new ArrayList<>();
        var world = this.client.world;
        if (world == null) return icons;

        // Collect valid spells
        List<SpellData> spells = new ArrayList<>();
        for (int i = 0; i < SpellChoiceScreenHandler.MAXIMUM_SPELL_COUNT; i++) {
            var rawId = handler.spellId[i];
            if (rawId < 0) continue;

            var spellEntry = SpellRegistry.from(world).getEntry(rawId);
            if (spellEntry.isEmpty()) continue;

            var spell = spellEntry.get();
            var spellId = spell.getKey().get().getValue();
            var spellName = Text.translatable(SpellTooltip.spellTranslationKey(spellId));
            var spellIcon = SpellRender.iconTexture(spellId);

            spells.add(new SpellData(i, new SpellViewModel(spellId, spellIcon, spellName)));
        }

        if (spells.isEmpty()) return icons;

        // Calculate centered positioning with 16px gaps
        int iconCount = spells.size();
        int totalIconWidth = iconCount * SPELL_ICON_SIZE;
        int totalGapWidth = (iconCount - 1) * ICON_SPACING;
        int totalWidth = totalIconWidth + totalGapWidth;

        // Position icons horizontally centered together
        int startX = (this.width - totalWidth) / 2;
        int y = this.height / 2 + SPELL_ICON_SIZE;  // Vertically centered, and below that a bit

        int x = startX;
        for (var spellData : spells) {
            icons.add(new SpellIconViewModel(
                spellData.index,
                x, y,
                SPELL_ICON_SIZE,
                spellData.spell
            ));
            x += SPELL_ICON_SIZE + ICON_SPACING;
        }

        return icons;
    }

    // Disable Slot clicks to protect the read-only slot
    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        // Do nothing
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        this.spellIcons = createSpellIcons();
    }

    private void drawSpellIcon(DrawContext context, SpellIconViewModel icon, int mouseX, int mouseY) {
        boolean mouseOver = icon.mouseOver(mouseX, mouseY);

        // Draw hover highlight (white overlay)
        if (mouseOver) {
            context.fill(icon.x - 1, icon.y - 1,
                    icon.x + icon.size + 1, icon.y + icon.size + 1,
                    0x40FFFFFF);  // 25% white
        }

        // Draw spell icon texture
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();

        if (icon.spell != null && icon.spell.icon != null) {
            context.drawTexture(icon.spell.icon, icon.x, icon.y,
                    0, 0, icon.size, icon.size, icon.size, icon.size);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw text
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("gui.spell_engine.choose_for_item", handler.getChoiceItemStack().getName()),
                this.width / 2, this.height / 2, 0xFFFFFF);

        // Draw spell details tooltip on hover (like SpellBindingScreen)
        var player = this.client.player;
        var itemStack = handler.getChoiceItemStack();

        for (var icon : spellIcons) {
            if (icon.mouseOver(mouseX, mouseY) && icon.spell != null) {
                ArrayList<Text> tooltip = new ArrayList<>();
                tooltip.addAll(SpellTooltip.spellEntry(icon.spell.id, player, itemStack, true, 0));
                context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {  // Left click
            for (var icon : spellIcons) {
                if (icon.mouseOver((int) mouseX, (int) mouseY)) {
                    // Send selection to server
                    if (this.client.interactionManager != null) {
                        this.client.interactionManager.clickButton(this.handler.syncId, icon.index);
                    }
                    this.close();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Draw spell icons (following SpellBindingScreen pattern)
        for (var icon : spellIcons) {
            drawSpellIcon(context, icon, mouseX, mouseY);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // View model records
    private record SpellViewModel(Identifier id, Identifier icon, Text name) { }

    private record SpellIconViewModel(
        int index,           // Index in handler.spellId array
        int x, int y,        // Screen coordinates
        int size,            // Icon size (16)
        SpellViewModel spell // Spell data
    ) {
        public boolean mouseOver(int mouseX, int mouseY) {
            return (mouseX >= x && mouseX < x + size) &&
                   (mouseY >= y && mouseY < y + size);
        }
    }

    private record SpellData(int index, SpellViewModel spell) { }
}
