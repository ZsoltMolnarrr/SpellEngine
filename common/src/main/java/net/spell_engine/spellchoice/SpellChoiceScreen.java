package net.spell_engine.spellchoice;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.gui.SpellTooltip;

import java.util.ArrayList;
import java.util.List;

@Environment(value = EnvType.CLIENT)
public class SpellChoiceScreen extends HandledScreen<SpellChoiceScreenHandler> {
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 10;

    private final List<ButtonWidget> spellButtons = new ArrayList<>();

    public SpellChoiceScreen(SpellChoiceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
        this.titleY = 6;

        // Create spell choice buttons
        updateButtons();
    }

    private void updateButtons() {
        spellButtons.clear();

        var world = this.client.world;
        if (world == null) return;

        List<SpellButtonData> buttons = new ArrayList<>();

        // Collect valid spells
        for (int i = 0; i < SpellChoiceScreenHandler.MAXIMUM_SPELL_COUNT; i++) {
            var rawId = handler.spellId[i];
            if (rawId < 0) continue;

            var spellEntry = SpellRegistry.from(world).getEntry(rawId);
            if (spellEntry.isEmpty()) continue;

            var spell = spellEntry.get();
            var spellId = spell.getKey().get().getValue();
            var spellName = Text.translatable(SpellTooltip.spellTranslationKey(spellId));

            buttons.add(new SpellButtonData(i, spellName, spell));
        }

        // Calculate horizontal layout
        int totalWidth = buttons.size() * BUTTON_WIDTH + (buttons.size() - 1) * BUTTON_SPACING;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height / 2;

        // Create buttons
        for (int i = 0; i < buttons.size(); i++) {
            var data = buttons.get(i);
            int x = startX + i * (BUTTON_WIDTH + BUTTON_SPACING);

            var button = ButtonWidget.builder(data.name, btn -> onSpellSelected(data.index))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

            spellButtons.add(button);
            this.addDrawableChild(button);
        }
    }

    private void onSpellSelected(int spellIndex) {
        // Send button click to server
        if (this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, spellIndex);
        }
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.updateButtons();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // No background texture - just render the buttons
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

    private record SpellButtonData(int index, Text name, RegistryEntry<Spell> spell) {}
}
