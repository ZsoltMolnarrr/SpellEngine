package net.spell_engine.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.item.UniversalSpellBookItem;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;

import java.util.ArrayList;

/**
 * Surfaces what the Spell Binding Table can do under its own EMI category:
 * <ul>
 *     <li>Spell Book creation: a plain Book becomes one of the spell book variants
 *     (one recipe per {@code spell_book/} spell tag, exactly what the table offers).</li>
 *     <li>Spell binding: a spell book plus lapis (or a Spell Scroll of the spell)
 *     yields the same book with that spell bound, mirroring how EMI lists enchanted
 *     books under anvil repairing.</li>
 * </ul>
 * Loaded reflectively by EMI only: Fabric via the {@code emi} entrypoint in {@code fabric.mod.json},
 * NeoForge via the {@link EmiEntrypoint} annotation scan. Nothing in Spell Engine references this
 * class, so it is never class-loaded when EMI is absent.
 */
@EmiEntrypoint
@Environment(EnvType.CLIENT)
public class SpellEngineEmiPlugin implements EmiPlugin {
    public static final EmiStack TABLE = EmiStack.of(SpellBindingBlock.ITEM);
    /** Category id {@code spell_engine:spell_binding} → name key {@code emi.category.spell_engine.spell_binding}. */
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            SpellBinding.ID, TABLE, TABLE, EmiRecipeSorting.none());

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CATEGORY);
        registry.addWorkstation(CATEGORY, TABLE);

        // Spell books and scrolls are single items whose variants differ only in components.
        // Without a comparison EMI would collapse every variant into one entry.
        // - Spell books are compared by pool only: EMI recipe lookup matches on outputs, so a book must be
        //   "equal" to its bound outputs for selecting it to list every binding recipe (same idea as EMI
        //   listing all enchanting recipes of a tool). The pool is what makes a spell book variant.
        // - Scrolls are compared by full components, one entry per spell.
        registry.setDefaultComparison(SpellEngineItems.SPELL_BOOK.get(), Comparison.compareData(SpellEngineEmiPlugin::poolOf));
        registry.setDefaultComparison(SpellEngineItems.SCROLL.get(), Comparison.compareComponents());

        // Spells and their tags are a data pack registry, EMI reloads plugins per world, so the client world is the source
        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        var wrapper = world.getRegistryManager().getOptionalWrapper(SpellRegistry.KEY);
        if (wrapper.isEmpty()) {
            return;
        }

        for (var tag : SpellBinding.availableSpellBookTags(world)) {
            var emptyBook = new ItemStack(SpellEngineItems.SPELL_BOOK.get());
            if (!UniversalSpellBookItem.applyFromTag(emptyBook, tag)) {
                continue;
            }
            if (SpellEngineMod.config.spell_book_creation_enabled) {
                registry.addRecipe(new SpellBookCreationEmiRecipe(
                        syntheticId("book", subId(tag.id())),
                        EmiStack.of(Items.BOOK),
                        EmiStack.of(emptyBook)));
            }

            var pool = SpellRegistry.entries(world, tag.id()).stream()
                    .sorted(SpellContainerHelper.catalogEntrySorter)
                    .toList();
            for (var spellEntry : pool) {
                if (spellEntry.getKey().isEmpty()) {
                    continue;
                }
                var spellId = spellEntry.getKey().get().getValue();
                var boundBook = emptyBook.copy();
                SpellContainerHelper.addSpell(world, spellId, boundBook);
                registry.addRecipe(new SpellBindingEmiRecipe(
                        syntheticId("spell", subId(tag.id()) + "/" + subId(spellId)),
                        EmiStack.of(emptyBook),
                        consumablesFor(wrapper.get(), spellEntry),
                        EmiStack.of(boundBook)));
            }
        }
    }

    /**
     * What the table accepts in its consumable slot to bind the given spell:
     * lapis (when the spell is learnable from the table's own catalog) and/or a scroll of the spell.
     * Multiple options cycle in the slot, like EMI's tag ingredients.
     */
    private static EmiIngredient consumablesFor(RegistryWrapper<Spell> wrapper, RegistryEntry<Spell> spellEntry) {
        var options = new ArrayList<EmiIngredient>();
        var spell = spellEntry.value();
        if (spell.learn != null && spell.tier > 0) {
            var lapisCost = spell.tier * spell.learn.level_cost_per_tier * SpellEngineMod.config.spell_binding_lapis_cost_multiplier;
            if (lapisCost > 0) {
                options.add(EmiStack.of(Items.LAPIS_LAZULI, lapisCost));
            }
        }
        var scroll = new ItemStack(SpellEngineItems.SCROLL.get());
        ScrollItem.applySpell(scroll, spellEntry, ScrollItem.resolveSpellPool(wrapper, spellEntry));
        options.add(EmiStack.of(scroll));
        return options.size() == 1 ? options.get(0) : EmiIngredient.of(options);
    }

    private static String poolOf(EmiStack stack) {
        var container = stack.getItemStack().get(SpellDataComponents.SPELL_CONTAINER);
        return container != null ? container.pool() : null;
    }

    /** Synthetic (non data-driven) recipe ids, in the same shape EMI uses for its own generated recipes. */
    private static Identifier syntheticId(String type, String name) {
        return Identifier.of(SpellEngineMod.ID, "/" + SpellBinding.name + "/" + type + "/" + name);
    }

    private static String subId(Identifier id) {
        return id.getNamespace() + "/" + id.getPath();
    }
}
