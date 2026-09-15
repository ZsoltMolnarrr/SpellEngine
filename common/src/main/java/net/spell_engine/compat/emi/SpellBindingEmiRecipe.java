package net.spell_engine.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Display of binding a single spell: spell book + consumable (lapis or a scroll) → the same book with the spell bound.
 * Laid out like EMI's own two-input recipes (anvil repairing / enchanting from books).
 */
@Environment(EnvType.CLIENT)
public class SpellBindingEmiRecipe implements EmiRecipe {
    private final Identifier id;
    private final EmiStack book;
    private final EmiIngredient consumable;
    private final EmiStack result;

    public SpellBindingEmiRecipe(Identifier id, EmiStack book, EmiIngredient consumable, EmiStack result) {
        this.id = id;
        this.book = book;
        this.consumable = consumable;
        this.result = result;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return SpellEngineEmiPlugin.CATEGORY;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(book, consumable);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(result);
    }

    /** Input and output are the same item, a recipe tree would recurse into itself (same as EMI's anvil recipes). */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public int getDisplayWidth() {
        return 125;
    }

    @Override
    public int getDisplayHeight() {
        return 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(EmiTexture.PLUS, 27, 3);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 75, 1);
        widgets.addSlot(book, 0, 0);
        widgets.addSlot(consumable, 49, 0);
        widgets.addSlot(result, 107, 0).recipeContext(this);
    }
}
