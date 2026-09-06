package net.spell_engine.mixin.loot;

import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// 1.20.1: `UniformLootNumberProvider` is a class with package-private `min`/`max` (a record with accessors on 1.21).
@Mixin(UniformLootNumberProvider.class)
public interface UniformLootNumberProviderAccessor {
    @Accessor("min")
    LootNumberProvider spellEngine_getMin();

    @Accessor("max")
    LootNumberProvider spellEngine_getMax();
}
