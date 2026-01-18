package net.spell_engine.api.item.trinket;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

@Deprecated(forRemoval = true)
public class SpellBookItem extends Item implements ISpellBookItem {
    private final Identifier poolId;
    public SpellBookItem(Identifier poolId, Settings settings) {
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
