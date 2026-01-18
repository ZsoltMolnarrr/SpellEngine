package net.spell_engine.fabric.compat.trinkets;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.trinket.ISpellBookItem;

@Deprecated(forRemoval = true)
public class LegacySpellBookTrinketItem extends SpellHostTrinketItem implements ISpellBookItem {
    private final Identifier poolId;

    public LegacySpellBookTrinketItem(Settings settings, Identifier poolId, SoundEvent equipSound) {
        super(settings, equipSound);
        this.poolId = poolId;
    }

    @Override
    public Identifier getPoolId() {
        return poolId;
    }
}
