package net.spell_engine.compat.trinkets;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.trinket.ISpellBookItem;

public class SpellBookTrinketItem extends SpellHostTrinketItem implements ISpellBookItem {
    private final Identifier poolId;

    public SpellBookTrinketItem(Settings settings, Identifier poolId, SoundEvent equipSound) {
        super(settings, equipSound);
        this.poolId = poolId;
    }

    @Override
    public Identifier getPoolId() {
        return poolId;
    }
}
