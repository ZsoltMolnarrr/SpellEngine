package net.spell_engine.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.item.UniversalSpellBookItem;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registers the Spell Binding category, its workstation, and every display:
 * spell book creation from a plain Book (one per {@code spell_book/} spell tag, exactly what the table offers),
 * and binding each spell of a pool into that pool's book via lapis or a Spell Scroll.
 * <p>
 * Displays are built here, on the client, rather than server-side: spells and their tags are a synced data pack
 * registry, so the client world already has everything the binding table itself uses to compute its offers.
 * <p>
 * Loaded reflectively by REI only: Fabric via the {@code rei_client} entrypoint in {@code fabric.mod.json},
 * NeoForge via the {@code @REIPluginClient} subclass in the neoforge module.
 */
@Environment(EnvType.CLIENT)
public class SpellEngineReiClientPlugin implements REIClientPlugin {
    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new SpellBindingCategory());
        registry.addWorkstations(SpellBindingDisplay.CATEGORY, EntryStacks.of(SpellBindingBlock.ITEM));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        for (var tag : SpellBinding.availableSpellBookTags(world)) {
            var emptyBook = new ItemStack(SpellEngineItems.SPELL_BOOK.get());
            if (!UniversalSpellBookItem.applyFromTag(emptyBook, tag)) {
                continue;
            }
            if (SpellEngineMod.config.spell_book_creation_enabled) {
                registry.add(new SpellBindingDisplay(
                        List.of(EntryIngredients.of(Items.BOOK)),
                        List.of(EntryIngredients.of(emptyBook)),
                        Optional.of(syntheticId("book", subId(tag.id())))));
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
                registry.add(new SpellBindingDisplay(
                        List.of(EntryIngredients.of(emptyBook), consumablesFor(world, spellEntry)),
                        List.of(EntryIngredients.of(boundBook)),
                        Optional.of(syntheticId("spell", subId(tag.id()) + "/" + subId(spellId)))));
            }
        }
    }

    /**
     * What the table accepts in its consumable slot to bind the given spell:
     * lapis (when the spell is learnable from the table's own catalog) and/or a scroll of the spell.
     * Multiple options cycle in the slot, like REI's tag ingredients.
     */
    private static EntryIngredient consumablesFor(World world, RegistryEntry<Spell> spellEntry) {
        var options = new ArrayList<ItemStack>();
        var spell = spellEntry.value();
        if (spell.learn != null && spell.tier > 0) {
            var lapisCost = spell.tier * spell.learn.level_cost_per_tier * SpellEngineMod.config.spell_binding_lapis_cost_multiplier;
            if (lapisCost > 0) {
                options.add(new ItemStack(Items.LAPIS_LAZULI, lapisCost));
            }
        }
        var scroll = new ItemStack(SpellEngineItems.SCROLL.get());
        ScrollItem.applySpell(scroll, spellEntry, ScrollItem.resolveSpellPool(world, spellEntry));
        options.add(scroll);
        return EntryIngredients.ofItemStacks(options);
    }

    /** Synthetic (non data-driven) display ids, one per generated display. */
    private static Identifier syntheticId(String type, String name) {
        return Identifier.of(SpellEngineMod.ID, SpellBinding.name + "/" + type + "/" + name);
    }

    private static String subId(Identifier id) {
        return id.getNamespace() + "/" + id.getPath();
    }
}
