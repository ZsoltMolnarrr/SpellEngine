package net.spell_engine.api.item.trinket;

import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class SpellBookTrinketItem extends TrinketItem implements SpellBookItem {
    private final Identifier poolId;

    public SpellBookTrinketItem(Identifier poolId, Settings settings) {
        super(settings);
        this.poolId = poolId;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public Identifier getPoolId() {
        return poolId;
    }

}
