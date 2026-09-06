package net.spell_engine.mixin.loot;

import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// 1.20.1: `ConstantLootNumberProvider` is a class with a package-private `value` (a record with accessors on 1.21).
@Mixin(ConstantLootNumberProvider.class)
public interface ConstantLootNumberProviderAccessor {
    @Accessor("value")
    float spellEngine_getValue();
}
