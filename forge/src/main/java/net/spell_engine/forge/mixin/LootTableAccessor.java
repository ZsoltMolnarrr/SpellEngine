package net.spell_engine.forge.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/// Read access to a loaded table's pools for `PlatformEventsImpl.onLootTableModify` (`existingPools()`).
/// Forge 47 patches `LootTable.pools` from the vanilla `LootPool[]` into a mutable `List<LootPool>` (backing
/// its `addPool`/`removePool`), so this accessor is Forge-only and must not be shared with the Fabric module.
@Mixin(LootTable.class)
public interface LootTableAccessor {
    @Accessor("pools")
    List<LootPool> spellEngine_getPools();
}
