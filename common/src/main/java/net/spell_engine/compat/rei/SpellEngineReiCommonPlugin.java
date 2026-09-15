package net.spell_engine.compat.rei;

import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.spellbinding.SpellBinding;

/**
 * Side-independent REI registrations for the Spell Binding Table.
 * <p>
 * Loaded reflectively by REI only: Fabric via the {@code rei_common} entrypoint in {@code fabric.mod.json},
 * NeoForge via the {@code @REIPluginCommon} subclass in the neoforge module. Nothing in Spell Engine references
 * this class, so it is never class-loaded when REI is absent.
 */
public class SpellEngineReiCommonPlugin implements REICommonPlugin {
    @Override
    public void registerDisplaySerializer(DisplaySerializerRegistry registry) {
        registry.register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, SpellBinding.name), SpellBindingDisplay.SERIALIZER);
    }

    /**
     * Spell books and scrolls are single items whose variants differ only in components; without a comparator
     * REI would collapse every variant into one entry.
     * <ul>
     *     <li>Spell books compare by pool only: REI looks recipes up by output, so a book must equal its bound
     *     variants for selecting it to list every binding display (as REI lists all enchanting recipes of a tool).
     *     The pool is what makes a spell book variant.</li>
     *     <li>Scrolls compare by full components, one entry per spell.</li>
     * </ul>
     */
    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.register(SpellEngineReiCommonPlugin::hashByPool, SpellEngineItems.SPELL_BOOK.get());
        registry.register(EntryComparator.itemComponents(), SpellEngineItems.SCROLL.get());
    }

    private static long hashByPool(me.shedaniel.rei.api.common.entry.comparison.ComparisonContext context, ItemStack stack) {
        var container = stack.get(SpellDataComponents.SPELL_CONTAINER);
        return container == null ? 0 : container.pool().hashCode();
    }
}
