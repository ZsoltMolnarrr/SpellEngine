package net.spell_engine.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class SlotModCompat {
    @Nullable
    public static Function<PlayerEntity, ItemStack> spellBookResolver = (player) -> ItemStack.EMPTY;
    public static ItemStack getEquippedSpellBook(PlayerEntity player) {
        return spellBookResolver.apply(player);
    }

    public record SpellBookArs(Identifier poolId, Item.Settings settings) { }
    @Nullable public static Function<SpellBookArs, ISpellBookItem> spellBookFactory = null;
    public static void setSpellBookFactory(Function<SpellBookArs, ISpellBookItem> factory) {
        if (spellBookFactory != null) { return; }
        spellBookFactory = factory;
    }

    public record SpellScrollArs(Item.Settings settings) { }
    @Nullable public static Function<SpellScrollArs, Item> spellScrollFactory = null;
    public static void setSpellScrollFactory(Function<SpellScrollArs, Item> factory) {
        if (spellScrollFactory != null) { return; }
        spellScrollFactory = factory;
    }
}
