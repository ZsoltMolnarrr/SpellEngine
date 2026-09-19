package net.spell_engine.mixin.loot;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SequenceFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// The functions a `minecraft:sequence` modifier chains (26.3: a loot entry with several functions carries one).
@Mixin(SequenceFunction.class)
public interface SequenceFunctionAccessor {
    @Accessor("functions")
    HolderSet<LootItemFunction> spellEngine_getFunctions();
}
