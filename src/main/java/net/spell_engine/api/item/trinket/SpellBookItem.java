package net.spell_engine.api.item.trinket;

import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class SpellBookItem extends TrinketItem {
    public final Identifier poolId;
    public SpellBookItem(Identifier poolId, Settings settings) {
        super(settings);
        this.poolId = poolId;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
