package net.spell_engine.compat.accessories;

import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.trinket.ISpellBookItem;

import java.util.function.Supplier;

@Deprecated(forRemoval = true)
public class LegacySpellBookAccessoryItem extends SpellHostAccessoryItem implements ISpellBookItem {
    private final Identifier poolId;

    public LegacySpellBookAccessoryItem(Item.Settings settings, Identifier poolId, Supplier<RegistryEntry<SoundEvent>> equipSound) {
        super(settings, equipSound);
        this.poolId = poolId;
    }

    @Override
    public Identifier getPoolId() {
        return poolId;
    }
}
