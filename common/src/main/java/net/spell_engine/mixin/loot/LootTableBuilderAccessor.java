package net.spell_engine.mixin.loot;

import com.google.common.collect.ImmutableList;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Read access to the pools collected so far by a {@link LootTable.Builder}.
/// `ImmutableList.Builder#build()` may be called repeatedly, so snapshotting is non-destructive.
@Mixin(LootTable.Builder.class)
public interface LootTableBuilderAccessor {
    @Accessor("pools")
    ImmutableList.Builder<LootPool> spellEngine_getPools();
}
