package net.spell_engine.mixin.loot;

import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import net.minecraft.loot.provider.number.LootNumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantWithLevelsLootFunction.class)
public interface EnchantWithLevelsLootFunctionAccessor {
    @Accessor("levels")
    LootNumberProvider spellEngine_getLevels();
}
