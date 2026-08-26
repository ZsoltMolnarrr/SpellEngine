package net.spell_engine.compat;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import net.minecraft.world.item.Item;

public class SlotModCompat {
    public record SpellScrollArs(Item.Properties settings) { }
    @Nullable public static Function<SpellScrollArs, Item> spellScrollFactory = null;
    public static void setSpellScrollFactory(Function<SpellScrollArs, Item> factory) {
        if (spellScrollFactory != null) { return; }
        spellScrollFactory = factory;
    }

    public record SpellBookArgs(Item.Properties settings) { }
    @Nullable public static Function<SpellBookArgs, Item> spellBookFactory = null;
    public static void setSpellBookFactory(Function<SpellBookArgs, Item> factory) {
        if (spellBookFactory != null) { return; }
        spellBookFactory = factory;
    }
}
