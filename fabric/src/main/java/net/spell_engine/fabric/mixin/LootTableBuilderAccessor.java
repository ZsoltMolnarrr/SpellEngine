package net.spell_engine.fabric.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/// Read access to the pools collected so far by a {@link LootTable.Builder}, for
/// `PlatformEventsImpl.onLootTableModify` (`existingPools()`). On 1.20.1 the builder keeps a mutable
/// `List<LootPool>` (the 1.21 line used an `ImmutableList.Builder`), so callers must snapshot it.
/// Fabric-only: Forge's `LootTableLoadEvent` hands out a built `LootTable` (see the forge `LootTableAccessor`).
@Mixin(LootTable.Builder.class)
public interface LootTableBuilderAccessor {
    @Accessor("pools")
    List<LootPool> spellEngine_getPools();
}
