package net.spell_engine.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.spell_engine.spellbinding.SpellBindingBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Spell Binding Table category. Two-input displays (binding) are laid out like REI's own anvil category,
 * single-input displays (spell book creation) like stonecutting.
 */
@Environment(EnvType.CLIENT)
public class SpellBindingCategory implements DisplayCategory<SpellBindingDisplay> {
    @Override
    public CategoryIdentifier<? extends SpellBindingDisplay> getCategoryIdentifier() {
        return SpellBindingDisplay.CATEGORY;
    }

    /** Reuses the binding table screen's own title, so no new lang key needs translating. */
    @Override
    public Component getTitle() {
        return Component.translatable("gui.spell_engine.spell_binding.title");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(SpellBindingBlock.ITEM);
    }

    @Override
    public int getDisplayHeight() {
        return 49;
    }

    @Override
    public List<Widget> setupDisplay(SpellBindingDisplay display, Rectangle bounds) {
        var inputs = display.getInputEntries();
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        if (inputs.size() >= 2) {
            // book [+] consumable [->] bound book, centred in the panel
            Point start = new Point(bounds.getCenterX() - 47, bounds.getCenterY() - 9);
            widgets.add(Widgets.createSlot(new Point(start.x, start.y)).entries(inputs.get(0)).markInput());
            widgets.add(Widgets.createSlot(new Point(start.x + 22, start.y)).entries(inputs.get(1)).markInput());
            widgets.add(Widgets.createArrow(new Point(start.x + 44, start.y)));
            widgets.add(Widgets.createResultSlotBackground(new Point(start.x + 76, start.y)));
            widgets.add(Widgets.createSlot(new Point(start.x + 76, start.y))
                    .entries(display.getOutputEntries().get(0)).disableBackground().markOutput());
        } else {
            // book [->] spell book
            Point start = new Point(bounds.getCenterX() - 36, bounds.getCenterY() - 9);
            widgets.add(Widgets.createSlot(new Point(start.x, start.y)).entries(inputs.get(0)).markInput());
            widgets.add(Widgets.createArrow(new Point(start.x + 22, start.y)));
            widgets.add(Widgets.createResultSlotBackground(new Point(start.x + 54, start.y)));
            widgets.add(Widgets.createSlot(new Point(start.x + 54, start.y))
                    .entries(display.getOutputEntries().get(0)).disableBackground().markOutput());
        }
        return widgets;
    }
}
