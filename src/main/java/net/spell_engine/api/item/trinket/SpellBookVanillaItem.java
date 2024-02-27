package net.spell_engine.api.item.trinket;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class SpellBookVanillaItem extends Item implements SpellBookItem {
    private final Identifier poolId;
    public SpellBookVanillaItem(Identifier poolId, Settings settings) {
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
