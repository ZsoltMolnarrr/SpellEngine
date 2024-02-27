package net.spell_engine.api.item.trinket;

import net.minecraft.item.ItemConvertible;
import net.minecraft.util.Identifier;

public interface SpellBookItem extends ItemConvertible {
    Identifier getPoolId();
}
